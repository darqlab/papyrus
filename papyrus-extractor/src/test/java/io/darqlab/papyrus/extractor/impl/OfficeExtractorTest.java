package io.darqlab.papyrus.extractor.impl;

import io.darqlab.papyrus.core.domain.ExtractedText;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class OfficeExtractorTest {

    private final OfficeExtractor extractor = new OfficeExtractor();

    @Test
    void supports_docx_returnsTrue() {
        assertTrue(extractor.supports(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }

    @Test
    void supports_pdf_returnsFalse() {
        assertFalse(extractor.supports("application/pdf"));
    }

    @Test
    void extract_docxWithParagraphs_returnsContent() throws IOException {
        byte[] docxBytes = buildDocx("First paragraph", "Second paragraph");

        ExtractedText result = extractor.extract(new ByteArrayInputStream(docxBytes), "test.docx");

        assertNotNull(result);
        assertEquals(1, result.pageCount());
        assertTrue(result.content().contains("First paragraph"));
        assertTrue(result.content().contains("Second paragraph"));
    }

    @Test
    void extract_emptyDocx_returnsEmptyContent() throws IOException {
        byte[] docxBytes = buildDocx();

        ExtractedText result = extractor.extract(new ByteArrayInputStream(docxBytes), "empty.docx");

        assertNotNull(result);
        assertTrue(result.content().isBlank());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private byte[] buildDocx(String... paragraphs) throws IOException {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            for (String text : paragraphs) {
                doc.createParagraph().createRun().setText(text);
            }
            doc.write(out);
            return out.toByteArray();
        }
    }
}
