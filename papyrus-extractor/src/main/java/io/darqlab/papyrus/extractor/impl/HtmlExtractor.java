package io.darqlab.papyrus.extractor.impl;

import io.darqlab.papyrus.extractor.DocumentExtractor;
import io.darqlab.papyrus.extractor.ExtractionException;
import io.darqlab.papyrus.extractor.ExtractedText;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

public class HtmlExtractor implements DocumentExtractor {

    private static final Set<String> SUPPORTED = Set.of("text/html");

    @Override
    public ExtractedText extract(InputStream inputStream, String filename) {
        try {
            Document doc = Jsoup.parse(inputStream, "UTF-8", "");
            // own() returns only direct text; wholeText() includes nested — use text() for clean output
            String content = doc.body() != null ? doc.body().text() : doc.text();
            return ExtractedText.of(content);

        } catch (IOException e) {
            throw new ExtractionException("Failed to extract text from HTML: " + filename, e);
        }
    }

    @Override
    public boolean supports(String mimeType) {
        return SUPPORTED.contains(mimeType);
    }
}
