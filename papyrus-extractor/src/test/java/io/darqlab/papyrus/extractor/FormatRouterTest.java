package io.darqlab.papyrus.extractor;

import io.darqlab.papyrus.extractor.impl.*;
import io.darqlab.papyrus.core.domain.ExtractedText;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class FormatRouterTest {

    private FormatRouter router;

    @BeforeEach
    void setUp() {
        router = FormatRouter.withDefaultExtractors();
    }

    @Test
    void route_pdf_usesDigitalPdfExtractor() throws IOException {
        byte[] pdf = buildPdf("Routed via PDF extractor");

        ExtractedText result = router.route(new ByteArrayInputStream(pdf), "document.pdf");

        assertTrue(result.content().contains("Routed via PDF extractor"));
    }

    @Test
    void route_docx_usesOfficeExtractor() throws IOException {
        byte[] docx = buildDocx("Routed via Office extractor");

        ExtractedText result = router.route(new ByteArrayInputStream(docx), "document.docx");

        assertTrue(result.content().contains("Routed via Office extractor"));
    }

    @Test
    void route_html_usesHtmlExtractor() {
        String html = "<html><body><p>Routed via HTML extractor</p></body></html>";

        ExtractedText result = router.route(toStream(html), "page.html");

        assertTrue(result.content().contains("Routed via HTML extractor"));
    }

    @Test
    void route_txt_usesPlainTextExtractor() {
        String text = "Routed via PlainText extractor";

        ExtractedText result = router.route(toStream(text), "notes.txt");

        assertEquals(text, result.content());
    }

    @Test
    void route_md_usesPlainTextExtractor() {
        String markdown = "# Heading\nRouted via PlainText extractor";

        ExtractedText result = router.route(toStream(markdown), "readme.md");

        assertTrue(result.content().contains("Heading"));
    }

    @Test
    void route_unknownExtension_throwsUnsupportedFormatException() {
        ExtractedText result = null;
        assertThrows(UnsupportedFormatException.class, () ->
                router.route(toStream("data"), "file.xyz"));
    }

    @Test
    void routeByMimeType_pdf_works() throws IOException {
        byte[] pdf = buildPdf("Direct MIME routing");

        ExtractedText result = router.routeByMimeType(
                new ByteArrayInputStream(pdf), "file", "application/pdf");

        assertTrue(result.content().contains("Direct MIME routing"));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private byte[] buildPdf(String text) throws IOException {
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
            return out.toByteArray();
        }
    }

    private byte[] buildDocx(String text) throws IOException {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText(text);
            doc.write(out);
            return out.toByteArray();
        }
    }

    private ByteArrayInputStream toStream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
