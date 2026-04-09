package io.darqlab.papyrus.extractor.impl;

import io.darqlab.papyrus.core.domain.ExtractedText;
import io.darqlab.papyrus.extractor.DocumentExtractor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Set;

/**
 * PDF extractor that automatically falls back to OCR for scanned documents.
 *
 * <p>First attempts digital text extraction via {@link DigitalPdfExtractor}.
 * If the result is sparse (average &lt; {@value #CHARS_PER_PAGE_THRESHOLD} chars/page),
 * the page is considered scanned and {@link OcrExtractor} is used instead.
 *
 * <p>This replaces {@link DigitalPdfExtractor} as the PDF handler in {@code FormatRouter}.
 */
public class SmartPdfExtractor implements DocumentExtractor {

    static final double CHARS_PER_PAGE_THRESHOLD = 100.0;

    private static final Set<String> SUPPORTED = Set.of("application/pdf");

    private final DigitalPdfExtractor digitalExtractor;
    private final OcrExtractor ocrExtractor;

    public SmartPdfExtractor() {
        this(new DigitalPdfExtractor(), new OcrExtractor());
    }

    /** Package-private for testing. */
    SmartPdfExtractor(DigitalPdfExtractor digitalExtractor, OcrExtractor ocrExtractor) {
        this.digitalExtractor = digitalExtractor;
        this.ocrExtractor     = ocrExtractor;
    }

    @Override
    public boolean supports(String mimeType) {
        return SUPPORTED.contains(mimeType);
    }

    @Override
    public ExtractedText extract(InputStream inputStream, String filename) {
        byte[] bytes;
        try {
            bytes = inputStream.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read PDF stream: " + filename, e);
        }

        ExtractedText digital = digitalExtractor.extract(new ByteArrayInputStream(bytes), filename);

        if (digital.averageCharsPerPage() >= CHARS_PER_PAGE_THRESHOLD) {
            return digital;
        }

        // Sparse text — likely a scanned PDF; fall back to OCR
        return ocrExtractor.extract(new ByteArrayInputStream(bytes), filename);
    }
}
