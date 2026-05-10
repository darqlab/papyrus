package io.darqlab.papyrus.extractor.impl;

import io.darqlab.papyrus.core.domain.ExtractedText;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
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

    @Test
    void extract_twoColumnTable_includesBothColumns() throws IOException {
        byte[] docxBytes = buildTwoColumnDocx("Left column content", "Right column content");

        ExtractedText result = extractor.extract(new ByteArrayInputStream(docxBytes), "two-column.docx");

        assertNotNull(result);
        assertTrue(result.content().contains("Left column content"),
                "Left column should be extracted");
        assertTrue(result.content().contains("Right column content"),
                "Right column should be extracted — was previously dropped by getParagraphs()");
    }

    @Test
    void extract_struckRun_wrapsWithAnnotation() throws IOException {
        byte[] docxBytes = buildDocxWithStrikethrough("old wording", false);

        ExtractedText result = extractor.extract(new ByteArrayInputStream(docxBytes), "struck.docx");

        assertTrue(result.content().contains("[STRUCK OUT: old wording]"),
                "Struck run should be annotated: " + result.content());
    }

    @Test
    void extract_doubleStrikeRun_wrapsWithAnnotation() throws IOException {
        byte[] docxBytes = buildDocxWithStrikethrough("double struck", true);

        ExtractedText result = extractor.extract(new ByteArrayInputStream(docxBytes), "dstruck.docx");

        assertTrue(result.content().contains("[STRUCK OUT: double struck]"),
                "Double-struck run should be annotated: " + result.content());
    }

    @Test
    void extract_nonStruckRun_noAnnotation() throws IOException {
        byte[] docxBytes = buildDocx("normal text");

        ExtractedText result = extractor.extract(new ByteArrayInputStream(docxBytes), "normal.docx");

        assertFalse(result.content().contains("[STRUCK OUT:"),
                "Normal text should not be annotated: " + result.content());
        assertTrue(result.content().contains("normal text"));
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

    private byte[] buildDocxWithStrikethrough(String text, boolean doubleStrike) throws IOException {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFRun run = doc.createParagraph().createRun();
            run.setText(text);
            if (doubleStrike) {
                run.setDoubleStrikethrough(true);
            } else {
                run.setStrikeThrough(true);
            }
            doc.write(out);
            return out.toByteArray();
        }
    }

    /** Simulates a two-column DOCX layout using a borderless table (one row, two cells). */
    private byte[] buildTwoColumnDocx(String leftText, String rightText) throws IOException {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XWPFTable table = doc.createTable(1, 2);
            XWPFTableRow row = table.getRow(0);
            row.getCell(0).getParagraphs().get(0).createRun().setText(leftText);
            row.getCell(1).getParagraphs().get(0).createRun().setText(rightText);

            doc.write(out);
            return out.toByteArray();
        }
    }
}
