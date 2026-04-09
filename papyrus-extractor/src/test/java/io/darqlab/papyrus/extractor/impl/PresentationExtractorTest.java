package io.darqlab.papyrus.extractor.impl;

import io.darqlab.papyrus.core.domain.ExtractedText;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class PresentationExtractorTest {

    private final PresentationExtractor extractor = new PresentationExtractor();

    @Test
    void supports_pptx_returnsTrue() {
        assertTrue(extractor.supports(
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"));
    }

    @Test
    void supports_pdf_returnsFalse() {
        assertFalse(extractor.supports("application/pdf"));
    }

    @Test
    void extract_pptxWithSlides_returnsSlideText() throws IOException {
        byte[] pptx = buildPptx("Intro to Papyrus", "Semantic search over your documents");

        ExtractedText result = extractor.extract(new ByteArrayInputStream(pptx), "deck.pptx");

        assertNotNull(result);
        assertEquals(2, result.pageCount());
        assertTrue(result.content().contains("Intro to Papyrus"),
                "Expected slide title in: " + result.content());
        assertTrue(result.content().contains("Semantic search"),
                "Expected slide body in: " + result.content());
    }

    @Test
    void extract_emptyPresentation_returnsBlank() throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ppt.write(out);
            byte[] pptx = out.toByteArray();

            ExtractedText result = extractor.extract(new ByteArrayInputStream(pptx), "empty.pptx");
            assertTrue(result.content().isBlank());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private byte[] buildPptx(String... slideTexts) throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String text : slideTexts) {
                var slide = ppt.createSlide();
                XSLFTextBox box = slide.createTextBox();
                box.setAnchor(new Rectangle2D.Double(50, 50, 600, 100));
                box.setText(text);
            }
            ppt.write(out);
            return out.toByteArray();
        }
    }
}
