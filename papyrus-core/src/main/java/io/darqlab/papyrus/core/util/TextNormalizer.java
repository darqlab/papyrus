package io.darqlab.papyrus.core.util;

/**
 * Utility for normalizing extracted text before chunking or embedding.
 */
public final class TextNormalizer {

    private TextNormalizer() {}

    /**
     * Normalize extracted text:
     * - Returns empty string for null input
     * - Replaces Windows line endings with Unix line endings
     * - Collapses 3+ consecutive blank lines into 2
     * - Trims leading/trailing whitespace
     */
    public static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    /**
     * Collapse all whitespace (spaces, tabs, newlines) to single spaces.
     * Used for single-line embedding inputs.
     */
    public static String flatten(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }
}
