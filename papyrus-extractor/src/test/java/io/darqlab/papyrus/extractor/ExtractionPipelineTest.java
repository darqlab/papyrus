package io.darqlab.papyrus.extractor;

import io.darqlab.papyrus.core.util.TextNormalizer;
import io.darqlab.papyrus.core.util.TokenEstimator;
import io.darqlab.papyrus.extractor.impl.DigitalPdfExtractor;
import io.darqlab.papyrus.extractor.impl.HtmlExtractor;
import io.darqlab.papyrus.extractor.impl.OfficeExtractor;
import io.darqlab.papyrus.extractor.impl.PlainTextExtractor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

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
                new TestCase("sample.pdf",  pdfStream("The quick brown fox"),    "quick brown fox"),
                new TestCase("sample.docx", docxStream("Document intelligence"), "Document intelligence"),
                new TestCase("sample.html", htmlStream("Semantic retrieval"),    "Semantic retrieval"),
                new TestCase("sample.txt",  textStream("Plain text content"),    "Plain text content"),
                new TestCase("sample.md",   textStream("## Markdown heading"),   "## Markdown heading"),
                new TestCase("sample.csv",  textStream("col1,col2\nval1,val2"),  "col1,col2")
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
}
