package io.darqlab.papyrus.extractor.impl;

import io.darqlab.papyrus.core.domain.ExtractedText;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    void extract_digitalPdfWithDenseText_doesNotInvokeOcr() {
        String denseText = "A".repeat(500);
        ExtractedText digitalResult = new ExtractedText(denseText, List.of(denseText), 1);

        DigitalPdfExtractor digital = mock(DigitalPdfExtractor.class);
        OcrExtractor ocr            = mock(OcrExtractor.class);

        when(digital.extract(any(), eq("doc.pdf"))).thenReturn(digitalResult);

        SmartPdfExtractor extractor = new SmartPdfExtractor(digital, ocr);
        ExtractedText result = extractor.extract(new ByteArrayInputStream(new byte[0]), "doc.pdf");

        assertSame(digitalResult, result);
        verifyNoInteractions(ocr);
    }

    @Test
    void extract_scannedPdfWithSparseText_fallsBackToOcr() {
        String sparseText = "hi"; // << 100 chars/page
        ExtractedText digitalResult = new ExtractedText(sparseText, List.of(sparseText), 1);
        ExtractedText ocrResult     = new ExtractedText("Full OCR text here.", List.of("Full OCR text here."), 1);

        DigitalPdfExtractor digital = mock(DigitalPdfExtractor.class);
        OcrExtractor ocr            = mock(OcrExtractor.class);

        when(digital.extract(any(), eq("scan.pdf"))).thenReturn(digitalResult);
        when(ocr.extract(any(), eq("scan.pdf"))).thenReturn(ocrResult);

        SmartPdfExtractor extractor = new SmartPdfExtractor(digital, ocr);
        ExtractedText result = extractor.extract(new ByteArrayInputStream(new byte[0]), "scan.pdf");

        assertSame(ocrResult, result);
        verify(ocr).extract(any(), eq("scan.pdf"));
    }

    @Test
    void extract_emptyPdf_fallsBackToOcr() {
        ExtractedText digitalResult = new ExtractedText("", List.of(), 0);
        ExtractedText ocrResult     = new ExtractedText("", List.of(), 0);

        DigitalPdfExtractor digital = mock(DigitalPdfExtractor.class);
        OcrExtractor ocr            = mock(OcrExtractor.class);

        when(digital.extract(any(), any())).thenReturn(digitalResult);
        when(ocr.extract(any(), any())).thenReturn(ocrResult);

        SmartPdfExtractor extractor = new SmartPdfExtractor(digital, ocr);
        extractor.extract(new ByteArrayInputStream(new byte[0]), "empty.pdf");

        verify(ocr).extract(any(), any());
    }

    @Test
    void threshold_exactlyAtBoundary_usesDigital() {
        // exactly 100 chars/page → digital (>= threshold)
        String text = "A".repeat(100);
        ExtractedText digitalResult = new ExtractedText(text, List.of(text), 1);

        DigitalPdfExtractor digital = mock(DigitalPdfExtractor.class);
        OcrExtractor ocr            = mock(OcrExtractor.class);

        when(digital.extract(any(), any())).thenReturn(digitalResult);

        SmartPdfExtractor extractor = new SmartPdfExtractor(digital, ocr);
        extractor.extract(new ByteArrayInputStream(new byte[0]), "boundary.pdf");

        verifyNoInteractions(ocr);
    }
}
