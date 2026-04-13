-- Links a re-ingested source back to the original archived source directory.
-- NULL means the source owns its own archive directory.
ALTER TABLE document_sources ADD COLUMN archive_source_id UUID REFERENCES document_sources(id) ON DELETE SET NULL;
