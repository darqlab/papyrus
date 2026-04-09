package io.darqlab.papyrus.extractor;

import io.darqlab.papyrus.core.util.MimeTypeDetector;
import io.darqlab.papyrus.core.domain.ExtractedText;
import io.darqlab.papyrus.extractor.impl.DigitalPdfExtractor;
import io.darqlab.papyrus.extractor.impl.HtmlExtractor;
import io.darqlab.papyrus.extractor.impl.OfficeExtractor;
import io.darqlab.papyrus.extractor.impl.PlainTextExtractor;

import java.io.InputStream;
import java.util.List;

/**
 * Routes a document to the appropriate extractor based on its MIME type.
 *
 * <p>Extractors are tried in order; the first that {@link DocumentExtractor#supports supports}
 * the MIME type wins. In Phase 2, OcrExtractor is added as a fallback for scanned PDFs.
 */
public class FormatRouter {

    private final List<DocumentExtractor> extractors;

    public FormatRouter(List<DocumentExtractor> extractors) {
        this.extractors = List.copyOf(extractors);
    }

    /**
     * Create a router pre-wired with all Phase 1 digital extractors.
     */
    public static FormatRouter withDefaultExtractors() {
        return new FormatRouter(List.of(
                new DigitalPdfExtractor(),
                new OfficeExtractor(),
                new HtmlExtractor(),
                new PlainTextExtractor()
        ));
    }

    /**
     * Route the input stream to an extractor and return the extracted text.
     *
     * @param inputStream the document bytes
     * @param filename    used for MIME detection and logging
     * @throws ExtractionException     if extraction fails
     * @throws UnsupportedFormatException if no extractor handles the detected MIME type
     */
    public ExtractedText route(InputStream inputStream, String filename) {
        String mimeType = MimeTypeDetector.detect(filename);
        return routeByMimeType(inputStream, filename, mimeType);
    }

    public ExtractedText routeByMimeType(InputStream inputStream, String filename, String mimeType) {
        return extractors.stream()
                .filter(e -> e.supports(mimeType))
                .findFirst()
                .orElseThrow(() -> new UnsupportedFormatException(mimeType, filename))
                .extract(inputStream, filename);
    }
}
