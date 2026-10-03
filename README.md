# HanScan V3 Smart — Android Native Alpha

Đây là bản chuyển kiến trúc từ web sang **Android native**.

## Đã làm trong V3 alpha

- CameraX camera sau, capture ưu tiên chất lượng.
- Chinese OCR model được bundle trong app (ML Kit Chinese) nên OCR không cần web/API dịch.
- Auto preprocessing bằng OpenCV:
  - tìm tứ giác trang/biển chữ lớn,
  - warp perspective,
  - CLAHE tăng tương phản,
  - sharpen,
  - multi-pass OCR và tự chọn kết quả tốt hơn.
- Fast path: ảnh sạch có đủ chữ sẽ dừng sau pass đầu để giữ cảm giác mượt.
- Canvas/View overlay native cho từng ký tự.
- **Vuốt từ chữ đầu đến chữ cuối** để chọn cả cụm.
- **Smart tap**: chạm một chữ, app tìm từ dài nhất trong từ điển local chứa chữ đó.
- **Character mode**: chạm đúng 1 chữ.
- Pinyin + nghĩa Việt dùng cho **toàn bộ vùng đang chọn**, không còn lỗi chọn 3 chữ nhưng chỉ dịch chữ đầu.
- TTS đọc tiếng Trung.
- Lưu flashcard offline.
- 255 từ/cụm Việt–Trung core có nhãn nguồn/confidence.
- Kiến trúc OCR tách riêng để thay ML Kit bằng PP-OCRv5/PP-OCRv6 mà không viết lại UI/camera/selection.

## Build

Yêu cầu khuyến nghị:

- Android Studio Quail 4 hoặc mới tương đương.
- JDK 17+.
- Android SDK 37 / Build Tools 36+.
- minSdk 26 (Android 8+).

Mở thư mục project trong Android Studio, Sync Gradle, sau đó Run trên điện thoại Android.

### Vì sao chưa có APK trong gói này?

Môi trường tạo project hiện tại không có Android SDK/Build Tools nên không thể ký/assemble APK ở đây. Source project đã được cấu hình theo AGP 9.4.1/Gradle 9.6.0; Android Studio có thể tải dependency và build APK.

## Nâng OCR lên PaddleOCR

PaddleOCR có Android SDK chính thức dựa trên ONNX Runtime, hỗ trợ PP-OCRv5_mobile và PP-OCRv6. File `PaddleOcrAdapter.java.disabled` ghi rõ điểm cắm engine. Khi thêm `ppocr-sdk` và model ONNX, output chỉ cần convert về `OcrResult`/`OcrChar` là toàn bộ gesture, dictionary và UI giữ nguyên.

## Data

Xem `DATA_SOURCES.md`.

`tools/build_full_dictionary.py` tạo SQLite data pack từ CC-CEDICT + Unicode Unihan do người dùng tải hợp lệ. HanScan cố ý không tự biến nghĩa tiếng Anh thành "nghĩa Việt verified".
