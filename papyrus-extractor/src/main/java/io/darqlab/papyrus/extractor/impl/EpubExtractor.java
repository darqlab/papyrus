package io.darqlab.papyrus.extractor.impl;

import io.darqlab.papyrus.core.domain.ExtractedText;
import io.darqlab.papyrus.extractor.DocumentExtractor;
import io.darqlab.papyrus.extractor.ExtractionException;
import org.jsoup.Jsoup;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts text from EPUB files.
 * An EPUB is a ZIP archive containing XHTML chapter files; each chapter is parsed with Jsoup.
 */
public class EpubExtractor implements DocumentExtractor {

    private static final Set<String> SUPPORTED = Set.of("application/epub+zip");

    @Override
    public ExtractedText extract(InputStream inputStream, String filename) {
        try {
            List<String> chapterTexts = new ArrayList<>();

            try (ZipInputStream zip = new ZipInputStream(inputStream)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName().toLowerCase();
                    if (!entry.isDirectory() && (name.endsWith(".html") || name.endsWith(".xhtml") || name.endsWith(".htm"))) {
                        byte[] bytes = zip.readAllBytes();
                        String text = Jsoup.parse(new ByteArrayInputStream(bytes), "UTF-8", "")
                                .body().text().strip();
                        if (!text.isBlank()) {
                            chapterTexts.add(text);
                        }
                    }
                    zip.closeEntry();
                }
            }

            String content = String.join("\n\n", chapterTexts);
            return new ExtractedText(content, chapterTexts, chapterTexts.size());

        } catch (IOException e) {
            throw new ExtractionException("Failed to extract text from EPUB: " + filename, e);
        }
    }

    @Override
    public boolean supports(String mimeType) {
        return SUPPORTED.contains(mimeType);
    }
}
