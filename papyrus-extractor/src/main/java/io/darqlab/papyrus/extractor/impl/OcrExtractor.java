package io.darqlab.papyrus.extractor.impl;

import io.darqlab.papyrus.core.domain.ExtractedText;
import io.darqlab.papyrus.extractor.DocumentExtractor;
import io.darqlab.papyrus.extractor.ExtractionException;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * OCR extractor for scanned PDFs.
 *
 * <p>Renders each page to a 300 DPI image using PDFBox, then passes it through
 * Tesseract. Not registered in the FormatRouter directly — used as a fallback
 * inside {@link SmartPdfExtractor} when digital extraction yields sparse text.
 */
public class OcrExtractor implements DocumentExtractor {

    private static final Set<String> SUPPORTED = Set.of("application/pdf");
    private static final float DPI = 300f;

    private Tesseract tesseract;
    private final String tessdata;
    private final String language;

    public OcrExtractor() {
        this(System.getenv().getOrDefault("TESSDATA_PREFIX", "/usr/share/tessdata"), "eng");
    }

    OcrExtractor(String tessdata, String language) {
        this.tessdata = tessdata;
        this.language = language;
    }

    /** Package-private for testing — allows injecting a pre-configured Tesseract. */
    OcrExtractor(Tesseract tesseract) {
        this.tesseract = tesseract;
        this.tessdata  = null;
        this.language  = null;
    }

    private Tesseract tesseract() {
        if (tesseract == null) {
            tesseract = new Tesseract();
            tesseract.setDatapath(tessdata);
            tesseract.setLanguage(language);
        }
        return tesseract;
    }

    @Override
    public boolean supports(String mimeType) {
        return SUPPORTED.contains(mimeType);
    }

    @Override
    public ExtractedText extract(InputStream inputStream, String filename) {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            List<String> pageTexts = new ArrayList<>(pageCount);

            for (int i = 0; i < pageCount; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, DPI);
                try {
                    pageTexts.add(tesseract().doOCR(image));
                } catch (TesseractException e) {
                    pageTexts.add("");
                }
            }

            String content = String.join("\n", pageTexts);
            return new ExtractedText(content, pageTexts, pageCount);

        } catch (IOException e) {
            throw new ExtractionException("OCR extraction failed for: " + filename, e);
        }
    }

}
