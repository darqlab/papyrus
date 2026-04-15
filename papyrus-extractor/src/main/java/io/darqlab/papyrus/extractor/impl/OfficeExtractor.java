package io.darqlab.papyrus.extractor.impl;

import io.darqlab.papyrus.extractor.DocumentExtractor;
import io.darqlab.papyrus.extractor.ExtractionException;
import io.darqlab.papyrus.core.domain.ExtractedText;
import org.apache.poi.xwpf.usermodel.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
            List<String> lines = new ArrayList<>();

            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph para) {
                    String text = para.getText();
                    if (!text.isBlank()) lines.add(text);
                } else if (element instanceof XWPFTable table) {
                    extractTableText(table, lines);
                }
            }

            return ExtractedText.of(String.join("\n", lines));

        } catch (IOException e) {
            throw new ExtractionException("Failed to extract text from Office document: " + filename, e);
        }
    }

    /**
     * Extracts text from a table row by row, joining cells with a tab so that
     * multi-column layouts (e.g. two-column handbook pages stored as borderless
     * tables) preserve both sides of the page.
     */
    private void extractTableText(XWPFTable table, List<String> lines) {
        for (XWPFTableRow row : table.getRows()) {
            List<String> cellTexts = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                StringBuilder cellContent = new StringBuilder();
                for (XWPFParagraph para : cell.getParagraphs()) {
                    String text = para.getText();
                    if (!text.isBlank()) {
                        if (!cellContent.isEmpty()) cellContent.append(" ");
                        cellContent.append(text);
                    }
                }
                if (!cellContent.isEmpty()) cellTexts.add(cellContent.toString());
            }
            if (!cellTexts.isEmpty()) lines.add(String.join("\t", cellTexts));
        }
    }

    @Override
    public boolean supports(String mimeType) {
        return SUPPORTED.contains(mimeType);
    }
}
