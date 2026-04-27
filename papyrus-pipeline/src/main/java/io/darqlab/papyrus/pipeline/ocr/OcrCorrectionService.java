package io.darqlab.papyrus.pipeline.ocr;

public interface OcrCorrectionService {
    boolean isEnabled();
    String correct(byte[] imageBytes, String mimeType, String rawOcrText);
}
