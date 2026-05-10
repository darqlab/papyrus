package io.darqlab.papyrus.extractor.impl;

import io.darqlab.papyrus.core.domain.ExtractedText;
import io.darqlab.papyrus.extractor.DocumentExtractor;
import io.darqlab.papyrus.extractor.ExtractionException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Extracts text from XLSX spreadsheets using Apache POI.
 * Each sheet becomes a labelled section; rows are joined with tabs, sheets with double newlines.
 */
public class SpreadsheetExtractor implements DocumentExtractor {

    private static final Set<String> SUPPORTED = Set.of(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private static final DataFormatter FORMATTER = new DataFormatter();

    @Override
    public ExtractedText extract(InputStream inputStream, String filename) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            List<String> sheetTexts = new ArrayList<>();

            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                List<String> rows = new ArrayList<>();

                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (Cell cell : row) {
                        String val = FORMATTER.formatCellValue(cell).strip();
                        if (!val.isBlank()) {
                            boolean struck = false;
                            if (cell instanceof XSSFCell xssfCell) {
                                XSSFFont font = xssfCell.getCellStyle().getFont();
                                struck = font != null && font.getStrikeout();
                            }
                            cells.add(struck ? "[STRUCK OUT: " + val + "]" : val);
                        }
                    }
                    if (!cells.isEmpty()) rows.add(String.join("\t", cells));
                }

                if (!rows.isEmpty()) {
                    sheetTexts.add("[Sheet: " + sheet.getSheetName() + "]\n" + String.join("\n", rows));
                }
            }

            return ExtractedText.of(String.join("\n\n", sheetTexts));

        } catch (IOException e) {
            throw new ExtractionException("Failed to extract text from spreadsheet: " + filename, e);
        }
    }

    @Override
    public boolean supports(String mimeType) {
        return SUPPORTED.contains(mimeType);
    }
}
