package io.darqlab.papyrus.extractor.impl;

import io.darqlab.papyrus.core.domain.ExtractedText;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class SpreadsheetExtractorTest {

    private final SpreadsheetExtractor extractor = new SpreadsheetExtractor();

    @Test
    void supports_xlsx_returnsTrue() {
        assertTrue(extractor.supports(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    void supports_pdf_returnsFalse() {
        assertFalse(extractor.supports("application/pdf"));
    }

    @Test
    void extract_xlsxWithData_returnsContent() throws IOException {
        byte[] xlsx = buildXlsx("Sheet1", new String[][]{
                {"Name",   "Score"},
                {"Alice",  "92"},
                {"Bob",    "85"}
        });

        ExtractedText result = extractor.extract(new ByteArrayInputStream(xlsx), "data.xlsx");

        assertNotNull(result);
        assertTrue(result.content().contains("Alice"), "Expected 'Alice' in: " + result.content());
        assertTrue(result.content().contains("Score"), "Expected 'Score' in: " + result.content());
        assertTrue(result.content().contains("Sheet1"), "Expected sheet name in: " + result.content());
    }

    @Test
    void extract_emptyWorkbook_returnsBlankContent() throws IOException {
        byte[] xlsx = buildXlsx("Empty", new String[0][]);

        ExtractedText result = extractor.extract(new ByteArrayInputStream(xlsx), "empty.xlsx");

        assertTrue(result.content().isBlank());
    }

    @Test
    void extract_strikeoutCell_wrapsWithAnnotation() throws IOException {
        byte[] xlsx = buildXlsxWithStrikeout("Sheet1", "superseded value");

        ExtractedText result = extractor.extract(new ByteArrayInputStream(xlsx), "struck.xlsx");

        assertTrue(result.content().contains("[STRUCK OUT: superseded value]"),
                "Strikeout cell should be annotated: " + result.content());
    }

    @Test
    void extract_normalCell_noAnnotation() throws IOException {
        byte[] xlsx = buildXlsx("Sheet1", new String[][]{{"active value"}});

        ExtractedText result = extractor.extract(new ByteArrayInputStream(xlsx), "normal.xlsx");

        assertFalse(result.content().contains("[STRUCK OUT:"),
                "Normal cell should not be annotated: " + result.content());
        assertTrue(result.content().contains("active value"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private byte[] buildXlsxWithStrikeout(String sheetName, String cellValue) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet(sheetName);
            XSSFFont font = wb.createFont();
            font.setStrikeout(true);
            XSSFCellStyle style = wb.createCellStyle();
            style.setFont(font);
            var cell = sheet.createRow(0).createCell(0);
            cell.setCellValue(cellValue);
            cell.setCellStyle(style);
            wb.write(out);
            return out.toByteArray();
        }
    }

    private byte[] buildXlsx(String sheetName, String[][] rows) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet(sheetName);
            for (int r = 0; r < rows.length; r++) {
                var row = sheet.createRow(r);
                for (int c = 0; c < rows[r].length; c++) {
                    row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }
}
