package com.hanscan.v3;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;

import com.google.common.util.concurrent.ListenableFuture;
import com.hanscan.v3.data.ChineseDictionary;
import com.hanscan.v3.data.FlashcardStore;
import com.hanscan.v3.data.SmartSegmenter;
import com.hanscan.v3.databinding.ActivityMainBinding;
import com.hanscan.v3.model.DictionaryEntry;
import com.hanscan.v3.model.OcrChar;
import com.hanscan.v3.model.OcrResult;
import com.hanscan.v3.ocr.ImagePreprocessor;
import com.hanscan.v3.ocr.MlKitChineseOcrEngine;
import com.hanscan.v3.ui.SelectionOverlayView;
import com.hanscan.v3.util.PinyinUtils;

import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SelectionOverlayView.Listener {
    private ActivityMainBinding b;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ImageCapture imageCapture;
    private MlKitChineseOcrEngine ocrEngine;
    private ChineseDictionary dictionary;
    private SmartSegmenter segmenter;
    private FlashcardStore flashcards;
    private OcrResult currentResult;
    private final List<Integer> selected = new ArrayList<>();
    private boolean smartMode = true;
    private TextToSpeech tts;

    private final ActivityResultLauncher<String> cameraPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else status("Cần quyền camera để quét chữ.");
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        dictionary = new ChineseDictionary(this);
        segmenter = new SmartSegmenter(dictionary);
        flashcards = new FlashcardStore(this);
        ocrEngine = new MlKitChineseOcrEngine();
        b.selectionOverlay.setListener(this);
        b.tvDictInfo.setText(dictionary.size() + " core");

        boolean cv = OpenCVLoader.initDebug();
        if (!cv) status("OpenCV chưa khởi tạo; OCR vẫn dùng được nhưng thiếu sửa góc.");

        tts = new TextToSpeech(this, code -> {
            if (code == TextToSpeech.SUCCESS) tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
        });

        b.btnCapture.setOnClickListener(v -> capture());
        b.btnRetake.setOnClickListener(v -> showCamera());
        b.btnSmartMode.setOnClickListener(v -> setSmartMode(true));
        b.btnCharMode.setOnClickListener(v -> setSmartMode(false));
        b.btnSpeak.setOnClickListener(v -> speakSelection());
        b.btnSave.setOnClickListener(v -> saveSelection());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera();
        else cameraPermission.launch(Manifest.permission.CAMERA);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(b.previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build();
                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
                status("Camera sẵn sàng · giữ máy ổn định rồi chụp");
            } catch (Exception e) { status("Không mở được camera: " + e.getMessage()); }
        }, ContextCompat.getMainExecutor(this));
    }

    private void capture() {
        if (imageCapture == null) { status("Camera chưa sẵn sàng."); return; }
        b.btnCapture.setEnabled(false);
        status("Đang chụp ảnh chất lượng cao…");
        File f = new File(getCacheDir(), "hanscan_capture.jpg");
        ImageCapture.OutputFileOptions opts = new ImageCapture.OutputFileOptions.Builder(f).build();
        imageCapture.takePicture(opts, worker, new ImageCapture.OnImageSavedCallback() {
            @Override public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                try {
                    Bitmap raw = BitmapFactory.decodeFile(f.getAbsolutePath());
                    Bitmap upright = rotateByExif(raw, f);
                    runOnUiThread(() -> status("Đang kiểm tra góc chụp và chất lượng ảnh…"));
                    List<ImagePreprocessor.Variant> variants = ImagePreprocessor.buildVariants(upright);
                    runOnUiThread(() -> runOcr(variants));
                } catch (Exception e) {
                    runOnUiThread(() -> { status("Lỗi xử lý ảnh: " + e.getMessage()); b.btnCapture.setEnabled(true); });
                }
            }
            @Override public void onError(@NonNull ImageCaptureException exc) {
                runOnUiThread(() -> { status("Chụp ảnh lỗi: " + exc.getMessage()); b.btnCapture.setEnabled(true); });
            }
        });
    }

    private Bitmap rotateByExif(Bitmap bitmap, File f) throws Exception {
        ExifInterface exif = new ExifInterface(f);
        int o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        float deg = 0;
        if (o == ExifInterface.ORIENTATION_ROTATE_90) deg = 90;
        else if (o == ExifInterface.ORIENTATION_ROTATE_180) deg = 180;
        else if (o == ExifInterface.ORIENTATION_ROTATE_270) deg = 270;
        if (deg == 0) return bitmap;
        Matrix m = new Matrix(); m.postRotate(deg);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
    }

    private void runOcr(List<ImagePreprocessor.Variant> variants) {
        status("OCR bắt đầu…");
        ocrEngine.recognizeBest(variants, new MlKitChineseOcrEngine.Callback() {
            @Override public void onSuccess(OcrResult result) {
                runOnUiThread(() -> showResult(result));
            }
            @Override public void onError(Exception error) {
                runOnUiThread(() -> { status("OCR lỗi: " + error.getMessage()); b.btnCapture.setEnabled(true); });
            }
            @Override public void onProgress(int current, int total, String variantName) {
                runOnUiThread(() -> status("OCR " + current + "/" + total + " · " + variantName));
            }
        });
    }

    private void showResult(OcrResult r) {
        currentResult = r;
        selected.clear();
        b.previewView.setVisibility(View.GONE);
        b.capturedImage.setVisibility(View.VISIBLE);
        b.capturedImage.setImageBitmap(r.bitmap);
        b.selectionOverlay.setData(r.chars, r.bitmap.getWidth(), r.bitmap.getHeight());
        b.btnCapture.setVisibility(View.GONE);
        b.btnRetake.setVisibility(View.VISIBLE);
        b.btnCapture.setEnabled(true);
        b.tvOcrInfo.setText("OCR: " + r.variantName + " · " + r.chars.size() + " chữ Hán · score " + String.format(Locale.US,"%.1f",r.qualityScore));
        status(r.chars.isEmpty() ? "Không thấy chữ Hán." : "Vuốt qua chữ hoặc chạm một từ.");
        clearSelectionUi();
    }

    private void showCamera() {
        currentResult = null; selected.clear();
        b.selectionOverlay.clearData();
        b.capturedImage.setVisibility(View.GONE);
        b.previewView.setVisibility(View.VISIBLE);
        b.btnCapture.setVisibility(View.VISIBLE);
        b.btnRetake.setVisibility(View.GONE);
        clearSelectionUi();
        status("Camera sẵn sàng.");
    }

    private void setSmartMode(boolean smart) {
        smartMode = smart;
        b.btnSmartMode.setStrokeColorResource(smart ? R.color.accent2 : R.color.line);
        b.btnCharMode.setStrokeColorResource(smart ? R.color.line : R.color.accent2);
        status(smart ? "Smart: chạm một chữ để đoán cả từ." : "Character: chạm đúng một chữ.");
    }

    @Override public void onTap(int index) {
        if (currentResult == null) return;
        List<Integer> ids = smartMode ? segmenter.smartSelection(currentResult.chars, index) : one(index);
        applySelection(ids);
    }

    @Override public void onDragSelection(List<Integer> indices) {
        if (currentResult == null) return;
        applySelection(indices);
    }

    private List<Integer> one(int i) { List<Integer> a = new ArrayList<>(); a.add(i); return a; }

    private void applySelection(List<Integer> ids) {
        selected.clear(); selected.addAll(ids);
        b.selectionOverlay.setSelectedIndices(selected);
        String text = selectedText();
        b.tvSelected.setText(text.isEmpty() ? "—" : text);
        DictionaryEntry e = dictionary.lookup(text);
        if (e != null) {
            b.tvPinyin.setText(e.pinyin);
            b.tvMeaning.setText(e.vi);
            b.tvSource.setText("✓ " + e.displaySource());
        } else {
            b.tvPinyin.setText(PinyinUtils.toPinyin(text));
            b.tvMeaning.setText(text.length() <= 2
                    ? "Chưa có nghĩa Việt đã xác minh trong Core V1."
                    : "Cụm này chưa có bản dịch Việt verified offline; vẫn giữ nguyên toàn bộ vùng bạn đã chọn.");
            b.tvSource.setText("Pinyin fallback · chưa xác minh nghĩa Việt");
        }
    }

    private String selectedText() {
        if (currentResult == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i : selected) if (i >= 0 && i < currentResult.chars.size()) sb.append(currentResult.chars.get(i).text);
        return sb.toString();
    }

    private void clearSelectionUi() {
        b.tvSelected.setText("—");
        b.tvPinyin.setText("Vuốt qua chữ hoặc chạm một từ");
        b.tvMeaning.setText("Nghĩa Việt đã xác minh sẽ hiện ở đây.");
        b.tvSource.setText("—");
    }

    private void speakSelection() {
        String text = selectedText();
        if (text.isEmpty()) { toast("Chưa chọn chữ"); return; }
        if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hanscan");
    }

    private void saveSelection() {
        String text = selectedText();
        if (text.isEmpty()) { toast("Chưa chọn chữ"); return; }
        DictionaryEntry e = dictionary.lookup(text);
        String p = e != null ? e.pinyin : PinyinUtils.toPinyin(text);
        String vi = e != null ? e.vi : "Chưa có nghĩa Việt verified";
        String src = e != null ? e.displaySource() : "UNVERIFIED";
        flashcards.add(text, p, vi, src);
        toast("Đã lưu flashcard");
    }

    private void status(String s) { b.tvStatus.setText(s); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    @Override protected void onDestroy() {
        super.onDestroy();
        worker.shutdown();
        if (ocrEngine != null) ocrEngine.close();
        if (tts != null) { tts.stop(); tts.shutdown(); }
    }
}
