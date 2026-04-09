package io.darqlab.papyrus.core.service;

import io.darqlab.papyrus.core.domain.IngestionJob;

import java.io.InputStream;
import java.util.Map;

public interface DocumentIngestionService {

    /**
     * Ingest a document from an input stream.
     *
     * @param inputStream the document content
     * @param filename    original filename (used for MIME detection and metadata)
     * @param language    Tesseract language code for OCR (e.g. "eng"); null uses the configured default
     * @param metadata    optional key-value metadata to attach to the source
     * @return an IngestionJob tracking the async processing
     */
    IngestionJob ingest(InputStream inputStream, String filename, String language, Map<String, Object> metadata);
}
