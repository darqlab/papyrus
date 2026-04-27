package io.darqlab.papyrus.extractor.impl;

import io.darqlab.papyrus.core.domain.ExtractedText;
import io.darqlab.papyrus.extractor.ExtractionException;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ImageOcrExtractorTest {

    @Test
    void supports_png_returnsTrue() {
        assertTrue(new ImageOcrExtractor().supports("image/png"));
    }

    @Test
    void supports_jpeg_returnsTrue() {
        assertTrue(new ImageOcrExtractor().supports("image/jpeg"));
    }

    @Test
    void supports_tiff_returnsTrue() {
        assertTrue(new ImageOcrExtractor().supports("image/tiff"));
    }

    @Test
    void supports_pdf_returnsFalse() {
        assertFalse(new ImageOcrExtractor().supports("application/pdf"));
    }

    @Test
    void supports_bmp_returnsTrue() {
        assertTrue(new ImageOcrExtractor().supports("image/bmp"));
    }

    @Test
    void supports_gif_returnsTrue() {
        assertTrue(new ImageOcrExtractor().supports("image/gif"));
    }

    @Test
    void extract_image_returnsOcrText() throws TesseractException, IOException {
        Tesseract tesseract = mock(Tesseract.class);
        when(tesseract.doOCR(any(BufferedImage.class))).thenReturn("Hello Papyrus");

        ImageOcrExtractor extractor = new ImageOcrExtractor(tesseract);
        ExtractedText result = extractor.extract(blankPngStream(), "scan.png");

        assertEquals("Hello Papyrus", result.content());
        assertEquals(1, result.pageCount());
        assertTrue(result.ocrUsed());
    }

    @Test
    void extract_ocrReturnsNull_returnsBlank() throws TesseractException, IOException {
        Tesseract tesseract = mock(Tesseract.class);
        when(tesseract.doOCR(any(BufferedImage.class))).thenReturn(null);

        ImageOcrExtractor extractor = new ImageOcrExtractor(tesseract);
        ExtractedText result = extractor.extract(blankPngStream(), "blank.png");

        assertTrue(result.content().isBlank());
    }

    @Test
    void extract_tesseractException_throwsExtractionException() throws TesseractException, IOException {
        Tesseract tesseract = mock(Tesseract.class);
        when(tesseract.doOCR(any(BufferedImage.class))).thenThrow(new TesseractException("OCR failure", null));

        ImageOcrExtractor extractor = new ImageOcrExtractor(tesseract);

        assertThrows(ExtractionException.class, () -> extractor.extract(blankPngStream(), "broken.png"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private InputStream blankPngStream() throws IOException {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return new ByteArrayInputStream(out.toByteArray());
    }
}
