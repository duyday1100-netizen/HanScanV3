package com.hanscan.v3.ocr;

import android.graphics.Rect;
import android.graphics.RectF;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.hanscan.v3.model.OcrChar;
import com.hanscan.v3.model.OcrResult;
import com.hanscan.v3.util.PinyinUtils;

import java.util.ArrayList;
import java.util.List;

public class MlKitChineseOcrEngine implements AutoCloseable {
    public interface Callback {
        void onSuccess(OcrResult result);
        void onError(Exception error);
        void onProgress(int current, int total, String variantName);
    }

    private final TextRecognizer recognizer = TextRecognition.getClient(
            new ChineseTextRecognizerOptions.Builder().build());

    public void recognizeBest(List<ImagePreprocessor.Variant> variants, Callback cb) {
        if (variants == null || variants.isEmpty()) {
            cb.onError(new IllegalArgumentException("No OCR variants")); return;
        }
        runVariant(variants, 0, null, cb);
    }

    private void runVariant(List<ImagePreprocessor.Variant> variants, int index, OcrResult best, Callback cb) {
        if (index >= variants.size()) {
            if (best != null) cb.onSuccess(best);
            else cb.onError(new IllegalStateException("OCR returned no result"));
            return;
        }
        ImagePreprocessor.Variant v = variants.get(index);
        cb.onProgress(index + 1, variants.size(), v.name);
        recognizer.process(InputImage.fromBitmap(v.bitmap, 0))
                .addOnSuccessListener(text -> {
                    OcrResult r = convert(v, text);
                    // Clean photos should feel instant: if the first pass is already strong, stop here.
                    if (index == 0 && r.chars.size() >= 12 && r.qualityScore >= 60f) {
                        cb.onSuccess(r);
                        return;
                    }
                    OcrResult newBest = best == null || r.qualityScore > best.qualityScore ? r : best;
                    runVariant(variants, index + 1, newBest, cb);
                })
                .addOnFailureListener(err -> runVariant(variants, index + 1, best, cb));
    }

    private OcrResult convert(ImagePreprocessor.Variant v, Text text) {
        List<OcrChar> chars = new ArrayList<>();
        int lineIndex = 0;
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                for (Text.Element el : line.getElements()) {
                    Rect box = el.getBoundingBox();
                    String s = el.getText();
                    if (box == null || s == null || s.isEmpty()) continue;
                    int[] cps = s.codePoints().toArray();
                    if (cps.length == 0) continue;
                    boolean vertical = box.height() > box.width() * 1.35f && cps.length > 1;
                    for (int k = 0; k < cps.length; k++) {
                        int cp = cps[k];
                        if (!PinyinUtils.isHan(cp)) continue;
                        RectF cb;
                        if (vertical) {
                            float h = box.height() / (float)cps.length;
                            cb = new RectF(box.left, box.top + k*h, box.right, box.top + (k+1)*h);
                        } else {
                            float w = box.width() / (float)cps.length;
                            cb = new RectF(box.left + k*w, box.top, box.left + (k+1)*w, box.bottom);
                        }
                        chars.add(new OcrChar(new String(Character.toChars(cp)), cb, lineIndex));
                    }
                }
                lineIndex++;
            }
        }
        String raw = text.getText() == null ? "" : text.getText();
        int han = chars.size();
        int rawCp = raw.codePointCount(0, raw.length());
        int nonEmptyLines = lineIndex;
        float score = han * 5f + Math.min(30, nonEmptyLines) * .6f - Math.max(0, rawCp - han * 3) * .05f;
        return new OcrResult(v.bitmap, chars, raw, score, v.name);
    }

    @Override public void close() { recognizer.close(); }
}
