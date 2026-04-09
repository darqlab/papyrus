CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE document_sources (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    filename     TEXT        NOT NULL,
    content_type TEXT        NOT NULL,
    file_size    BIGINT,
    page_count   INT,
    language     TEXT        DEFAULT 'eng',
    status       TEXT        NOT NULL,
    error        TEXT,
    metadata     JSONB,
    created_at   TIMESTAMPTZ DEFAULT now(),
    updated_at   TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE document_chunks (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id   UUID        NOT NULL REFERENCES document_sources(id) ON DELETE CASCADE,
    chunk_index INT         NOT NULL,
    page_number INT,
    content     TEXT        NOT NULL,
    embedding   vector(512),
    token_count INT,
    metadata    JSONB,
    created_at  TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX ON document_chunks USING hnsw (embedding vector_cosine_ops);

CREATE TABLE ingestion_jobs (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    status      TEXT        NOT NULL,
    total       INT,
    processed   INT         DEFAULT 0,
    failed      INT         DEFAULT 0,
    created_at  TIMESTAMPTZ DEFAULT now(),
    updated_at  TIMESTAMPTZ DEFAULT now()
);
