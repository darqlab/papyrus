-- Per-source chunking config. NULL means: use global default from application config.
ALTER TABLE document_sources
    ADD COLUMN chunking_strategy       VARCHAR(32),
    ADD COLUMN chunking_max_tokens     INT,
    ADD COLUMN chunking_overlap_tokens INT;
