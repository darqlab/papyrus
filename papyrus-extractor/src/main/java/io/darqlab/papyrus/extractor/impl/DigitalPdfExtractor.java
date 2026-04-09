package io.darqlab.papyrus.extractor.impl;

import io.darqlab.papyrus.extractor.DocumentExtractor;
import io.darqlab.papyrus.extractor.ExtractionException;
import io.darqlab.papyrus.core.domain.ExtractedText;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DigitalPdfExtractor implements DocumentExtractor {

    private static final Set<String> SUPPORTED = Set.of("application/pdf");

    @Override
    public ExtractedText extract(InputStream inputStream, String filename) {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            int pageCount = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();

            List<String> pageTexts = new ArrayList<>(pageCount);
            for (int i = 1; i <= pageCount; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                pageTexts.add(stripper.getText(document));
            }

            String fullContent = String.join("\n", pageTexts);
            return new ExtractedText(fullContent, pageTexts, pageCount);

        } catch (IOException e) {
            throw new ExtractionException("Failed to extract text from PDF: " + filename, e);
        }
    }

    @Override
    public boolean supports(String mimeType) {
        return SUPPORTED.contains(mimeType);
    }
}
