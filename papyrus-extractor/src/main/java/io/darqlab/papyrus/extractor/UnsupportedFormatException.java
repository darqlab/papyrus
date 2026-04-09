package io.darqlab.papyrus.extractor;

public class UnsupportedFormatException extends ExtractionException {

    public UnsupportedFormatException(String mimeType, String filename) {
        super("No extractor available for MIME type '" + mimeType + "' (file: " + filename + ")");
    }
}
