package io.darqlab.papyrus.core.domain;

import java.util.List;

/**
 * Result of document text extraction.
 *
 * @param content   full concatenated text across all pages
 * @param pageTexts text extracted per page (index 0 = page 1); empty for single-page formats
 * @param pageCount total number of pages (1 for non-paginated formats)
 * @param ocrUsed   true if OCR was used during extraction (images or scanned PDFs)
 */
public record ExtractedText(
        String content,
        List<String> pageTexts,
        int pageCount,
        boolean ocrUsed
) {
    /** Convenience constructor for single-page / non-paginated formats. */
    public static ExtractedText of(String content) {
        return new ExtractedText(content, List.of(content), 1, false);
    }

    /** Convenience constructor for OCR-produced single-page text. */
    public static ExtractedText ofOcr(String content) {
        return new ExtractedText(content, List.of(content), 1, true);
    }

    /** Average characters per page — used for scanned PDF detection. */
    public double averageCharsPerPage() {
        if (pageCount == 0) return 0;
        return (double) content.length() / pageCount;
    }
}
