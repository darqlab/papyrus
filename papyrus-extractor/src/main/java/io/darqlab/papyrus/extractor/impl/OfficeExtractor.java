package io.darqlab.papyrus.extractor.impl;

import io.darqlab.papyrus.extractor.DocumentExtractor;
import io.darqlab.papyrus.extractor.ExtractionException;
import io.darqlab.papyrus.extractor.ExtractedText;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Extracts text from DOCX files using Apache POI.
 * Phase 1 scope: DOCX only. XLSX and PPTX are added in Phase 8.
 */
public class OfficeExtractor implements DocumentExtractor {

    private static final Set<String> SUPPORTED = Set.of(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    @Override
    public ExtractedText extract(InputStream inputStream, String filename) {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            String content = document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .filter(text -> !text.isBlank())
                    .collect(Collectors.joining("\n"));

            return ExtractedText.of(content);

        } catch (IOException e) {
            throw new ExtractionException("Failed to extract text from Office document: " + filename, e);
        }
    }

    @Override
    public boolean supports(String mimeType) {
        return SUPPORTED.contains(mimeType);
    }
}
