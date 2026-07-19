package io.darqlab.papyrus.ingestor.reingest;

import java.util.UUID;

/**
 * One row read from the <em>live/source</em> database's {@code document_sources} table
 * (see {@link IngestorProperties#db()}'s {@code sourceDatabase}), restricted to rows with
 * a non-null {@code archive_filename} (the population this batch loop can rebuild).
 *
 * <p>Deliberately a plain record, not JPA-mapped: {@code papyrus-ingestor} talks to the
 * source and target databases via raw JDBC (see {@link ReingestOrchestrator} class javadoc),
 * not Spring Data.
 *
 * @param id                     the source's own UUID in the <em>source</em> database
 * @param filename               original filename
 * @param contentType            original MIME type
 * @param chunkingStrategy       stored per-source chunking strategy name, or {@code null}
 *                               to fall back to the global default
 * @param chunkingMaxTokens      stored per-source max tokens, or {@code null} for default
 * @param chunkingOverlapTokens  stored per-source overlap tokens, or {@code null} for default
 * @param archiveFilename        the archive-relative filename (never {@code null} — callers
 *                               filter for this)
 * @param archiveSourceId        if non-null, this source's archive lives under another
 *                               source's directory (a prior re-ingest); if {@code null},
 *                               this source owns its own archive directory (keyed by {@link #id()})
 */
record SourceRow(
        UUID id,
        String filename,
        String contentType,
        String chunkingStrategy,
        Integer chunkingMaxTokens,
        Integer chunkingOverlapTokens,
        String archiveFilename,
        UUID archiveSourceId
) {
    /**
     * Resolves the archive directory ID this source's text actually lives under — its own
     * ID if it owns the archive, or {@link #archiveSourceId()} if it points at another
     * source's shared archive directory. Mirrors
     * {@code DocumentService.reingest}'s {@code archiveDirId} resolution.
     */
    UUID archiveDirId() {
        return archiveSourceId != null ? archiveSourceId : id;
    }
}
