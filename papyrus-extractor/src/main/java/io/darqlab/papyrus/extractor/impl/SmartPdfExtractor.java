package io.darqlab.papyrus.extractor.impl;

import io.darqlab.papyrus.core.domain.ExtractedText;
import io.darqlab.papyrus.extractor.DocumentExtractor;
import io.darqlab.papyrus.extractor.ExtractionException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * PDF extractor that automatically falls back to OCR on a per-page basis.
 *
 * <p>For each page, digital text extraction is attempted first. If a page yields fewer than
 * {@value #CHARS_PER_PAGE_THRESHOLD} characters it is considered image-only and OCR is used
 * for that page. This handles pure-image PDFs, mixed PDFs, and digital PDFs correctly.
 *
 * <p>This replaces {@link DigitalPdfExtractor} as the PDF handler in {@code FormatRouter}.
 */
public class SmartPdfExtractor implements DocumentExtractor {

    static final double CHARS_PER_PAGE_THRESHOLD = 100.0;

    private static final Set<String> SUPPORTED = Set.of("application/pdf");

    private final OcrExtractor ocrExtractor;

    public SmartPdfExtractor() {
        this(new OcrExtractor());
    }

    /** Package-private for testing. */
    SmartPdfExtractor(OcrExtractor ocrExtractor) {
        this.ocrExtractor = ocrExtractor;
    }

    @Override
    public boolean supports(String mimeType) {
        return SUPPORTED.contains(mimeType);
    }

    @Override
    public ExtractedText extract(InputStream inputStream, String filename) {
        byte[] bytes;
        try {
            bytes = inputStream.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read PDF stream: " + filename, e);
        }

        try (PDDocument document = Loader.loadPDF(bytes)) {
            int pageCount = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(document);
            List<String> pageTexts = new ArrayList<>(pageCount);
            boolean ocrUsed = false;

            for (int i = 0; i < pageCount; i++) {
                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);
                String digitalText = stripper.getText(document);

                if (digitalText.length() >= CHARS_PER_PAGE_THRESHOLD) {
                    pageTexts.add(digitalText);
                } else {
                    pageTexts.add(ocrExtractor.extractPage(document, renderer, i));
                    ocrUsed = true;
                }
            }

            String content = String.join("\n", pageTexts);
            return new ExtractedText(content, pageTexts, pageCount, ocrUsed);

        } catch (IOException e) {
            throw new ExtractionException("Failed to extract text from PDF: " + filename, e);
        }
    }
}
