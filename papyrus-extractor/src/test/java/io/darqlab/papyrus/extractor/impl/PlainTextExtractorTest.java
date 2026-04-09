package io.darqlab.papyrus.extractor.impl;

import io.darqlab.papyrus.extractor.ExtractedText;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PlainTextExtractorTest {

    private final PlainTextExtractor extractor = new PlainTextExtractor();

    @ParameterizedTest
    @ValueSource(strings = {"text/plain", "text/markdown", "text/csv"})
    void supports_textTypes_returnsTrue(String mimeType) {
        assertTrue(extractor.supports(mimeType));
    }

    @Test
    void supports_pdf_returnsFalse() {
        assertFalse(extractor.supports("application/pdf"));
    }

    @Test
    void extract_plainText_returnsFullContent() {
        String content = "Line one\nLine two\nLine three";

        ExtractedText result = extractor.extract(toStream(content), "notes.txt");

        assertEquals(content, result.content());
        assertEquals(1, result.pageCount());
    }

    @Test
    void extract_markdown_returnsRawMarkdown() {
        String markdown = "# Heading\n\nSome **bold** text.\n\n- item 1\n- item 2";

        ExtractedText result = extractor.extract(toStream(markdown), "readme.md");

        assertTrue(result.content().contains("# Heading"));
        assertTrue(result.content().contains("bold"));
    }

    @Test
    void extract_csv_returnsRawCsv() {
        String csv = "name,age,city\nAlice,30,Manila\nBob,25,Cebu";

        ExtractedText result = extractor.extract(toStream(csv), "data.csv");

        assertTrue(result.content().contains("Alice"));
        assertTrue(result.content().contains("Manila"));
    }

    @Test
    void extract_emptyFile_returnsEmptyContent() {
        ExtractedText result = extractor.extract(toStream(""), "empty.txt");

        assertEquals("", result.content());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ByteArrayInputStream toStream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
