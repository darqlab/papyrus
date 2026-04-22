package io.darqlab.papyrus.extractor.impl;

import io.darqlab.papyrus.core.domain.ExtractedText;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SmartPdfExtractorTest {

    @Test
    void supports_pdf_returnsTrue() {
        assertTrue(new SmartPdfExtractor().supports("application/pdf"));
    }

    @Test
    void supports_docx_returnsFalse() {
        assertFalse(new SmartPdfExtractor().supports(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }

    @Test
    void extract_digitalPdf_noOcr() throws IOException {
        String denseText = "A".repeat(200);
        byte[] pdfBytes = buildPdf(denseText);

        OcrExtractor ocr = mock(OcrExtractor.class);
        SmartPdfExtractor extractor = new SmartPdfExtractor(ocr);

        ExtractedText result = extractor.extract(new ByteArrayInputStream(pdfBytes), "doc.pdf");

        assertFalse(result.ocrUsed());
        assertTrue(result.content().contains("A"));
        verifyNoInteractions(ocr);
    }

    @Test
    void extract_blankPage_ocrUsed() throws IOException {
        byte[] pdfBytes = buildPdf(null); // blank page — no text layer

        OcrExtractor ocr = mock(OcrExtractor.class);
        when(ocr.extractPage(any(PDDocument.class), any(PDFRenderer.class), eq(0)))
                .thenReturn("OCR extracted text");

        SmartPdfExtractor extractor = new SmartPdfExtractor(ocr);
        ExtractedText result = extractor.extract(new ByteArrayInputStream(pdfBytes), "scan.pdf");

        assertTrue(result.ocrUsed());
        assertEquals("OCR extracted text", result.content());
        verify(ocr).extractPage(any(PDDocument.class), any(PDFRenderer.class), eq(0));
    }

    @Test
    void extract_mixedPdf_ocrOnlyOnSparsePages() throws IOException {
        // page 0: dense text; page 1: blank (image-only)
        byte[] pdfBytes = buildMultiPagePdf("A".repeat(200), null);

        OcrExtractor ocr = mock(OcrExtractor.class);
        when(ocr.extractPage(any(), any(), eq(1))).thenReturn("OCR page 1");

        SmartPdfExtractor extractor = new SmartPdfExtractor(ocr);
        ExtractedText result = extractor.extract(new ByteArrayInputStream(pdfBytes), "mixed.pdf");

        assertTrue(result.ocrUsed());
        assertEquals(2, result.pageCount());
        verify(ocr, never()).extractPage(any(), any(), eq(0));
        verify(ocr).extractPage(any(), any(), eq(1));
    }

    @Test
    void extract_allBlankPages_allOcr() throws IOException {
        byte[] pdfBytes = buildMultiPagePdf(null, null);

        OcrExtractor ocr = mock(OcrExtractor.class);
        when(ocr.extractPage(any(), any(), anyInt())).thenReturn("ocr text");

        SmartPdfExtractor extractor = new SmartPdfExtractor(ocr);
        ExtractedText result = extractor.extract(new ByteArrayInputStream(pdfBytes), "all-images.pdf");

        assertTrue(result.ocrUsed());
        assertEquals(2, result.pageCount());
        verify(ocr).extractPage(any(), any(), eq(0));
        verify(ocr).extractPage(any(), any(), eq(1));
    }

    @Test
    void extract_exactlyAtThreshold_usesDigital() throws IOException {
        // CHARS_PER_PAGE_THRESHOLD == 100.0; exactly 100 chars must use digital, not OCR
        String text = "A".repeat((int) SmartPdfExtractor.CHARS_PER_PAGE_THRESHOLD);
        byte[] pdfBytes = buildPdf(text);

        OcrExtractor ocr = mock(OcrExtractor.class);
        SmartPdfExtractor extractor = new SmartPdfExtractor(ocr);
        ExtractedText result = extractor.extract(new ByteArrayInputStream(pdfBytes), "boundary.pdf");

        assertFalse(result.ocrUsed());
        verifyNoInteractions(ocr);
    }

    @Test
    void extract_sparseText_usesOcr() throws IOException {
        // PDFTextStripper adds trailing whitespace; use a clearly sub-threshold value (10 chars)
        // to ensure digital extraction yields < CHARS_PER_PAGE_THRESHOLD regardless of padding.
        String text = "A".repeat(10);
        byte[] pdfBytes = buildPdf(text);

        OcrExtractor ocr = mock(OcrExtractor.class);
        when(ocr.extractPage(any(), any(), eq(0))).thenReturn("ocr result");

        SmartPdfExtractor extractor = new SmartPdfExtractor(ocr);
        ExtractedText result = extractor.extract(new ByteArrayInputStream(pdfBytes), "sparse.pdf");

        assertTrue(result.ocrUsed());
        verify(ocr).extractPage(any(), any(), eq(0));
    }

    @Test
    void extract_digitalPdf_pageCountMatches() throws IOException {
        byte[] pdfBytes = buildMultiPagePdf("A".repeat(200), "B".repeat(200), "C".repeat(200));

        OcrExtractor ocr = mock(OcrExtractor.class);
        SmartPdfExtractor extractor = new SmartPdfExtractor(ocr);
        ExtractedText result = extractor.extract(new ByteArrayInputStream(pdfBytes), "three-pages.pdf");

        assertFalse(result.ocrUsed());
        assertEquals(3, result.pageCount());
        assertEquals(3, result.pageTexts().size());
        verifyNoInteractions(ocr);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Builds a single-page PDF. Pass null for a blank page (no text layer). */
    private byte[] buildPdf(String text) throws IOException {
        return buildMultiPagePdf(text);
    }

    /** Builds a multi-page PDF. Null entries produce blank pages. */
    private byte[] buildMultiPagePdf(String... pageTexts) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String text : pageTexts) {
                PDPage page = new PDPage();
                doc.addPage(page);
                if (text != null && !text.isEmpty()) {
                    try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                        cs.beginText();
                        cs.setFont(font, 12);
                        cs.newLineAtOffset(50, 700);
                        cs.showText(text);
                        cs.endText();
                    }
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
