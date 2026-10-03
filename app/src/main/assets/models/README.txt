PaddleOCR model slot for the next engine switch.

Official Android PaddleOCR SDK expects:
  models/det/inference.onnx
  models/rec/inference.onnx
  models/rec/inference.yml

HanScan V3 alpha builds with bundled ML Kit Chinese OCR so it is usable before these model files are added.
See app/src/main/java/com/hanscan/v3/ocr/PaddleOcrAdapter.java.disabled.
