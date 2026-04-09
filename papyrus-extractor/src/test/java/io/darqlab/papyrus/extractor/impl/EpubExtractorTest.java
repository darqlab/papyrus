package io.darqlab.papyrus.extractor.impl;

import io.darqlab.papyrus.core.domain.ExtractedText;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class EpubExtractorTest {

    private final EpubExtractor extractor = new EpubExtractor();

    @Test
    void supports_epub_returnsTrue() {
        assertTrue(extractor.supports("application/epub+zip"));
    }

    @Test
    void supports_pdf_returnsFalse() {
        assertFalse(extractor.supports("application/pdf"));
    }

    @Test
    void extract_epubWithChapters_returnsContent() throws IOException {
        byte[] epub = buildEpub(
                "OEBPS/chapter1.xhtml", "<html><body><p>Chapter One content here.</p></body></html>",
                "OEBPS/chapter2.xhtml", "<html><body><p>Chapter Two explores Papyrus.</p></body></html>"
        );

        ExtractedText result = extractor.extract(new ByteArrayInputStream(epub), "book.epub");

        assertNotNull(result);
        assertEquals(2, result.pageCount());
        assertTrue(result.content().contains("Chapter One"),
                "Expected 'Chapter One' in: " + result.content());
        assertTrue(result.content().contains("Chapter Two"),
                "Expected 'Chapter Two' in: " + result.content());
    }

    @Test
    void extract_epubWithNonHtmlEntries_skipsNonHtml() throws IOException {
        byte[] epub = buildEpub(
                "OEBPS/chapter1.xhtml", "<html><body><p>Real content.</p></body></html>",
                "META-INF/container.xml", "<container/>",
                "OEBPS/style.css", "body { font-size: 1em; }"
        );

        ExtractedText result = extractor.extract(new ByteArrayInputStream(epub), "book.epub");

        assertEquals(1, result.pageCount(), "Only 1 HTML chapter expected");
        assertTrue(result.content().contains("Real content"));
    }

    @Test
    void extract_emptyEpub_returnsBlank() throws IOException {
        byte[] epub = buildEpub();

        ExtractedText result = extractor.extract(new ByteArrayInputStream(epub), "empty.epub");

        assertTrue(result.content().isBlank());
        assertEquals(0, result.pageCount());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Build a minimal EPUB ZIP with alternating name/content pairs. */
    private byte[] buildEpub(String... nameContentPairs) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(baos)) {
            for (int i = 0; i < nameContentPairs.length; i += 2) {
                zip.putNextEntry(new ZipEntry(nameContentPairs[i]));
                zip.write(nameContentPairs[i + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            return baos.toByteArray();
        }
    }
}
