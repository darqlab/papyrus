package io.darqlab.papyrus.extractor.impl;

import io.darqlab.papyrus.core.domain.ExtractedText;
import io.darqlab.papyrus.extractor.DocumentExtractor;
import io.darqlab.papyrus.extractor.ExtractionException;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * OCR extractor for raster image formats (PNG, JPG, TIFF, BMP, GIF).
 *
 * <p>Reads the image with {@link ImageIO} and passes it directly to Tesseract.
 * Tesseract is initialized lazily on first use.
 */
public class ImageOcrExtractor implements DocumentExtractor {

    private static final Set<String> SUPPORTED = Set.of(
            "image/png",
            "image/jpeg",
            "image/tiff",
            "image/bmp",
            "image/gif"
    );

    private Tesseract tesseract;
    private final String tessdata;
    private final String language;

    public ImageOcrExtractor() {
        this(System.getenv().getOrDefault("TESSDATA_PREFIX", "/usr/share/tessdata"), "eng");
    }

    ImageOcrExtractor(String tessdata, String language) {
        this.tessdata = tessdata;
        this.language = language;
    }

    /** Package-private for testing. */
    ImageOcrExtractor(Tesseract tesseract) {
        this.tesseract = tesseract;
        this.tessdata  = null;
        this.language  = null;
    }

    @Override
    public boolean supports(String mimeType) {
        return SUPPORTED.contains(mimeType);
    }

    @Override
    public ExtractedText extract(InputStream inputStream, String filename) {
        try {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                throw new ExtractionException("Could not decode image: " + filename, null);
            }
            String text = tesseract().doOCR(image);
            return ExtractedText.of(text != null ? text.strip() : "");
        } catch (IOException e) {
            throw new ExtractionException("Failed to read image: " + filename, e);
        } catch (TesseractException e) {
            throw new ExtractionException("OCR failed for image: " + filename, e);
        }
    }

    private Tesseract tesseract() {
        if (tesseract == null) {
            tesseract = new Tesseract();
            tesseract.setDatapath(tessdata);
            tesseract.setLanguage(language);
        }
        return tesseract;
    }
}
