package io.darqlab.papyrus.extractor.impl;

import io.darqlab.papyrus.extractor.DocumentExtractor;
import io.darqlab.papyrus.extractor.ExtractionException;
import io.darqlab.papyrus.extractor.ExtractedText;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class PlainTextExtractor implements DocumentExtractor {

    private static final Set<String> SUPPORTED = Set.of(
            "text/plain",
            "text/markdown",
            "text/csv"
    );

    @Override
    public ExtractedText extract(InputStream inputStream, String filename) {
        try {
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return ExtractedText.of(content);

        } catch (IOException e) {
            throw new ExtractionException("Failed to read plain text file: " + filename, e);
        }
    }

    @Override
    public boolean supports(String mimeType) {
        return SUPPORTED.contains(mimeType);
    }
}
