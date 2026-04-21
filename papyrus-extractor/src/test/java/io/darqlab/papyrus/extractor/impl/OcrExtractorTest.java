package io.darqlab.papyrus.extractor.impl;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OcrExtractorTest {

    @Test
    void safeDpi_standardLetterPage_returns300() throws Exception {
        // 8.5" × 11" in PDF points = 612 × 792
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(new PDRectangle(612, 792)));
            float dpi = OcrExtractor.safeDpi(doc, 0);
            assertEquals(300f, dpi, 0.01f);
        }
    }

    @Test
    void safeDpi_largeScannedPage_capsBelow300() throws Exception {
        // Scanner-produced page: 2642 × 3432 pts (1 px = 1 pt at 72 ppi)
        // Uncapped 300 DPI → 11 008 × 14 300 px → ~600 MB BufferedImage
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(new PDRectangle(2642, 3432)));
            float dpi = OcrExtractor.safeDpi(doc, 0);
            assertTrue(dpi < 300f, "DPI should be capped below 300 for large scanner pages, got: " + dpi);
            // Verify longest rendered side stays within MAX_RENDER_PIXELS
            float longestInch = 3432f / 72f;
            float renderedPixels = longestInch * dpi;
            assertTrue(renderedPixels <= OcrExtractor.MAX_RENDER_PIXELS + 1,
                    "Rendered longest side should not exceed MAX_RENDER_PIXELS, got: " + renderedPixels);
        }
    }

    @Test
    void safeDpi_a4Page_returns300() throws Exception {
        // A4: 595 × 842 pts
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(new PDRectangle(595, 842)));
            float dpi = OcrExtractor.safeDpi(doc, 0);
            assertEquals(300f, dpi, 0.01f);
        }
    }
}
