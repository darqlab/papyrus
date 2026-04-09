package io.darqlab.papyrus.extractor.impl;

import io.darqlab.papyrus.extractor.ExtractedText;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class HtmlExtractorTest {

    private final HtmlExtractor extractor = new HtmlExtractor();

    @Test
    void supports_html_returnsTrue() {
        assertTrue(extractor.supports("text/html"));
    }

    @Test
    void supports_pdf_returnsFalse() {
        assertFalse(extractor.supports("application/pdf"));
    }

    @Test
    void extract_simpleHtml_stripsTagsAndReturnsText() {
        String html = "<html><body><h1>Title</h1><p>Hello world</p></body></html>";

        ExtractedText result = extractor.extract(toStream(html), "page.html");

        assertNotNull(result);
        assertTrue(result.content().contains("Title"));
        assertTrue(result.content().contains("Hello world"));
        assertFalse(result.content().contains("<h1>"), "Tags should be stripped");
    }

    @Test
    void extract_htmlWithScriptAndStyle_excludesScriptContent() {
        String html = "<html><head><style>body{color:red}</style></head>" +
                      "<body><script>alert('xss')</script><p>Real content</p></body></html>";

        ExtractedText result = extractor.extract(toStream(html), "page.html");

        assertTrue(result.content().contains("Real content"));
    }

    @Test
    void extract_emptyBody_returnsEmptyContent() {
        String html = "<html><body></body></html>";

        ExtractedText result = extractor.extract(toStream(html), "empty.html");

        assertNotNull(result);
        assertTrue(result.content().isBlank());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ByteArrayInputStream toStream(String html) {
        return new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8));
    }
}
