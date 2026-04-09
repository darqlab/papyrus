package io.darqlab.papyrus.extractor.impl;

import io.darqlab.papyrus.extractor.ExtractedText;
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
}
