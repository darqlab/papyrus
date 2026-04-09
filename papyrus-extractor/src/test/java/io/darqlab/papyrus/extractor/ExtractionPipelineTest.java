package io.darqlab.papyrus.extractor;

import io.darqlab.papyrus.core.util.TextNormalizer;
import io.darqlab.papyrus.core.domain.ExtractedText;
import io.darqlab.papyrus.core.util.TokenEstimator;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end extraction pipeline test.
 * Verifies: document bytes → FormatRouter → ExtractedText → TextNormalizer → TokenEstimator.
 */
class ExtractionPipelineTest {

    private FormatRouter router;

    @BeforeEach
    void setUp() {
        router = FormatRouter.withDefaultExtractors();
    }

    record TestCase(String filename, InputStream stream, String expectedFragment) {}

    static Stream<TestCase> documentFormats() throws IOException {
        return Stream.of(
                new TestCase("sample.pdf",  pdfStream("The quick brown fox jumps over the lazy dog. "
                        .repeat(4)),    "quick brown fox"),
                new TestCase("sample.docx", docxStream("Document intelligence"), "Document intelligence"),
                new TestCase("sample.html", htmlStream("Semantic retrieval"),    "Semantic retrieval"),
                new TestCase("sample.txt",  textStream("Plain text content"),    "Plain text content"),
                new TestCase("sample.md",   textStream("## Markdown heading"),   "## Markdown heading"),
                new TestCase("sample.csv",  textStream("col1,col2\nval1,val2"),  "col1,col2"),
                new TestCase("sample.xlsx", xlsxStream("Spreadsheet data"),      "Spreadsheet data"),
                new TestCase("sample.pptx", pptxStream("Presentation content"),  "Presentation content"),
                new TestCase("sample.epub", epubStream("EPUB chapter text"),     "EPUB chapter text")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("documentFormats")
    void pipeline_extractNormalizEstimate_allFormats(TestCase tc) {
        // 1. Route and extract
        ExtractedText extracted = router.route(tc.stream(), tc.filename());

        assertNotNull(extracted, "ExtractedText should not be null");
        assertFalse(extracted.content().isBlank(), "Extracted content should not be blank");
        assertTrue(extracted.content().contains(tc.expectedFragment()),
                "Expected '" + tc.expectedFragment() + "' in: " + extracted.content());

        // 2. Normalize
        String normalized = TextNormalizer.normalize(extracted.content());
        assertFalse(normalized.isBlank());

        // 3. Estimate tokens
        int tokens = TokenEstimator.estimate(normalized);
        assertTrue(tokens > 0, "Token estimate should be positive");
    }

    // ── Fixture builders ─────────────────────────────────────────────────────

    private static InputStream pdfStream(String text) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            doc.save(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private static InputStream docxStream(String text) throws IOException {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText(text);
            doc.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private static InputStream htmlStream(String bodyText) {
        String html = "<html><body><p>" + bodyText + "</p></body></html>";
        return new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8));
    }

    private static InputStream textStream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static InputStream xlsxStream(String cellValue) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.createSheet("Sheet1").createRow(0).createCell(0).setCellValue(cellValue);
            wb.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private static InputStream pptxStream(String slideText) throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSLFTextBox box = ppt.createSlide().createTextBox();
            box.setAnchor(new Rectangle2D.Double(50, 50, 600, 100));
            box.setText(slideText);
            ppt.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private static InputStream epubStream(String chapterText) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(baos)) {
            zip.putNextEntry(new ZipEntry("OEBPS/chapter1.xhtml"));
            String html = "<html><body><p>" + chapterText + "</p></body></html>";
            zip.write(html.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            return new ByteArrayInputStream(baos.toByteArray());
        }
    }
}
