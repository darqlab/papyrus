ALTER TABLE document_sources
    ADD COLUMN content_hash TEXT,
    ADD COLUMN source_url   TEXT;

CREATE UNIQUE INDEX uq_document_sources_content_hash
    ON document_sources (content_hash)
    WHERE content_hash IS NOT NULL;

CREATE INDEX idx_document_sources_source_url
    ON document_sources (source_url)
    WHERE source_url IS NOT NULL;
