package io.darqlab.papyrus.extractor;

import java.util.List;

/**
 * Result of document text extraction.
 *
 * @param content   full concatenated text across all pages
 * @param pageTexts text extracted per page (index 0 = page 1); empty for single-page formats
 * @param pageCount total number of pages (1 for non-paginated formats)
 */
public record ExtractedText(
        String content,
        List<String> pageTexts,
        int pageCount
) {
    /** Convenience constructor for single-page / non-paginated formats. */
    public static ExtractedText of(String content) {
        return new ExtractedText(content, List.of(content), 1);
    }

    /** Average characters per page — used for scanned PDF detection. */
    public double averageCharsPerPage() {
        if (pageCount == 0) return 0;
        return (double) content.length() / pageCount;
    }
}
