package io.darqlab.papyrus.extractor;

import java.io.InputStream;

public interface DocumentExtractor {

    /**
     * Extract text from the given input stream.
     *
     * @param inputStream the document content
     * @param filename    original filename (used for context/logging, not routing)
     * @return extracted text with page breakdown
     * @throws ExtractionException if extraction fails
     */
    ExtractedText extract(InputStream inputStream, String filename);

    /**
     * Returns true if this extractor handles the given MIME type.
     */
    boolean supports(String mimeType);
}
