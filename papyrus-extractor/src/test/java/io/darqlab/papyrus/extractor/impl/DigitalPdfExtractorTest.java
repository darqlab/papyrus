package io.darqlab.papyrus.extractor.impl;

import io.darqlab.papyrus.core.domain.ExtractedText;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DigitalPdfExtractorTest {

    private final DigitalPdfExtractor extractor = new DigitalPdfExtractor();

    @Test
    void supports_pdf_returnsTrue() {
        assertTrue(extractor.supports("application/pdf"));
    }

    @Test
    void supports_otherType_returnsFalse() {
        assertFalse(extractor.supports("text/html"));
    }

    @Test
    void extract_singlePagePdf_returnsContent() throws IOException {
        byte[] pdfBytes = buildPdf("Hello from Papyrus");

        ExtractedText result = extractor.extract(new ByteArrayInputStream(pdfBytes), "test.pdf");

        assertNotNull(result);
        assertEquals(1, result.pageCount());
        assertEquals(1, result.pageTexts().size());
        assertTrue(result.content().contains("Hello from Papyrus"),
                "Expected 'Hello from Papyrus' in: " + result.content());
    }

    @Test
    void extract_multiPagePdf_returnsAllPages() throws IOException {
        byte[] pdfBytes = buildMultiPagePdf("Page one text", "Page two text");

        ExtractedText result = extractor.extract(new ByteArrayInputStream(pdfBytes), "multi.pdf");

        assertEquals(2, result.pageCount());
        assertEquals(2, result.pageTexts().size());
        assertTrue(result.content().contains("Page one text"));
        assertTrue(result.content().contains("Page two text"));
    }

    @Test
    void extract_averageCharsPerPage_isPositive() throws IOException {
        byte[] pdfBytes = buildPdf("Some content for testing chars per page metric");

        ExtractedText result = extractor.extract(new ByteArrayInputStream(pdfBytes), "test.pdf");

        assertTrue(result.averageCharsPerPage() > 0);
    }

    @Test
    void extract_redTextOnly_wrapsWithStruckOutAnnotation() throws IOException {
        byte[] pdfBytes = buildColoredPdf(true, "old policy");

        ExtractedText result = extractor.extract(new ByteArrayInputStream(pdfBytes), "red.pdf");

        assertTrue(result.content().contains("[STRUCK OUT: old policy]"),
                "Red text should be annotated as struck out: " + result.content());
    }

    @Test
    void extract_blackTextOnly_noAnnotation() throws IOException {
        byte[] pdfBytes = buildPdf("current policy");

        ExtractedText result = extractor.extract(new ByteArrayInputStream(pdfBytes), "black.pdf");

        assertFalse(result.content().contains("[STRUCK OUT:"),
                "Black text should not be annotated: " + result.content());
        assertTrue(result.content().contains("current policy"));
    }

    @Test
    void extract_mixedColors_annotatesOnlyRed() throws IOException {
        byte[] pdfBytes = buildMixedColorPdf("old clause", " active clause");

        ExtractedText result = extractor.extract(new ByteArrayInputStream(pdfBytes), "mixed.pdf");

        assertTrue(result.content().contains("[STRUCK OUT: old clause]"),
                "Red text should be annotated: " + result.content());
        assertFalse(result.content().contains("[STRUCK OUT: active clause]"),
                "Black text should not be annotated: " + result.content());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private byte[] buildPdf(String text) throws IOException {
        return buildMultiPagePdf(text);
    }

    private byte[] buildMultiPagePdf(String... pageTexts) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String pageText : pageTexts) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(font, 12);
                    cs.newLineAtOffset(50, 700);
                    cs.showText(pageText);
                    cs.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private byte[] buildColoredPdf(boolean red, String text) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(50, 700);
                if (red) {
                    cs.setNonStrokingColor(1f, 0f, 0f);
                }
                cs.showText(text);
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** Red text on line 1 (y=700), black text on line 2 (y=680) — separate text objects, mirrors real Word PDF layout. */
    private byte[] buildMixedColorPdf(String redText, String blackText) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(50, 700);
                cs.setNonStrokingColor(1f, 0f, 0f);
                cs.showText(redText);
                cs.endText();

                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(50, 680);
                cs.setNonStrokingColor(0f, 0f, 0f);
                cs.showText(blackText);
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
