package io.darqlab.papyrus.ingestor.reingest;

import io.darqlab.papyrus.core.domain.IngestionStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JDBC-connection-scoped writer for the <em>target</em> per-model database
 * ({@code papyrus_<model>}), used by {@link ReingestOrchestrator}.
 *
 * <p>Not built on {@code VectorStoreService} — that class is bound to Spring's
 * single-autoconfigured {@code DataSource}/JPA {@code EntityManager}, and
 * {@code papyrus-ingestor} deliberately excludes that autoconfiguration because a run
 * touches two different databases (see {@code IngestorApplication}'s and
 * {@code DatabaseBootstrapper}'s class javadocs). This class instead takes a plain
 * {@link Connection} per call — the caller (the orchestrator) owns connection lifecycle.
 *
 * <p>The {@code document_chunks} INSERT mirrors {@code VectorStoreService.storeChunks}'s
 * SQL exactly (same column list, same {@code ?::vector} cast). The {@code document_sources}
 * INSERT/UPDATE statements are a hand-rolled equivalent of what
 * {@code DocumentSourceRepository}/JPA would generate, since JPA isn't available here.
 */
public class TargetDbWriter {

    /**
     * Looks up an existing {@code document_sources} row's status in the target DB by id.
     * Used to implement skip-DONE / drop-and-rebuild resumability. Note that the id passed
     * in is always the <em>source</em> database's own row id, reused verbatim as the target
     * row's id (see {@link ReingestOrchestrator} class javadoc, "ID reuse" note) — so this
     * lookup is stable across ingestor runs, not a freshly-generated id each time.
     */
    public Optional<String> findStatus(Connection conn, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT status FROM document_sources WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(rs.getString("status"));
            }
        }
    }

    /**
     * Deletes a {@code document_sources} row. {@code document_chunks.source_id} has
     * {@code ON DELETE CASCADE} (V1 schema), so its chunks are removed automatically —
     * used for the "drop-and-rebuild" resumability path (stale PROCESSING/FAILED rows).
     */
    public void deleteSource(Connection conn, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM document_sources WHERE id = ?")) {
            ps.setObject(1, id);
            ps.executeUpdate();
        }
    }

    /**
     * Inserts a fresh {@code document_sources} row for a reingested source (write-before-process:
     * called before chunking/embedding so a mid-run crash leaves a visible {@code PROCESSING} row).
     *
     * <p>{@code archive_source_id} is deliberately always inserted {@code NULL} here, even if
     * {@code row.archiveSourceId()} is non-null — see {@link #updateArchiveSourceId} and the
     * {@link ReingestOrchestrator} class javadoc "ID reuse" note for why (same-database FK,
     * insert-order/owner-not-yet-migrated hazards).
     */
    public void insertSource(Connection conn, UUID id, SourceRow row, IngestionStatus status) throws SQLException {
        String sql = """
                INSERT INTO document_sources
                    (id, filename, content_type, language, status, archive_filename, archive_source_id,
                     chunking_strategy, chunking_max_tokens, chunking_overlap_tokens, created_at, updated_at)
                VALUES (?, ?, ?, 'eng', ?, ?, NULL, ?, ?, ?, now(), now())
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setString(2, row.filename());
            ps.setString(3, row.contentType());
            ps.setString(4, status.name());
            ps.setString(5, row.archiveFilename());
            ps.setString(6, row.chunkingStrategy());
            setNullableInt(ps, 7, row.chunkingMaxTokens());
            setNullableInt(ps, 8, row.chunkingOverlapTokens());
            ps.executeUpdate();
        }
    }

    /**
     * Best-effort backfill of {@code archive_source_id} once the referenced owner row is
     * expected to exist in the target DB too (called by the orchestrator in a pass after the
     * main per-source loop, so insertion order no longer matters). Throws if the owner row
     * genuinely isn't present (FK violation) — the caller treats that as non-fatal per-row.
     */
    public void updateArchiveSourceId(Connection conn, UUID id, UUID archiveSourceId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE document_sources SET archive_source_id = ?, updated_at = now() WHERE id = ?")) {
            ps.setObject(1, archiveSourceId);
            ps.setObject(2, id);
            ps.executeUpdate();
        }
    }

    /** Updates a source's status (and optional error message) after processing. */
    public void updateSourceStatus(Connection conn, UUID id, IngestionStatus status, String error) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE document_sources SET status = ?, error = ?, updated_at = now() WHERE id = ?")) {
            ps.setString(1, status.name());
            ps.setString(2, error);
            ps.setObject(3, id);
            ps.executeUpdate();
        }
    }

    /**
     * Persists a batch of chunks with their embeddings for a source. Mirrors
     * {@code VectorStoreService.storeChunks}'s SQL exactly.
     */
    public void storeChunks(Connection conn, UUID sourceId, List<String> contents,
                             List<List<Float>> embeddings, List<Integer> tokenCounts) throws SQLException {
        String sql = """
                INSERT INTO document_chunks
                    (id, source_id, chunk_index, page_number, content, embedding, token_count)
                VALUES (?, ?, ?, ?, ?, ?::vector, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < contents.size(); i++) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, sourceId);
                ps.setInt(3, i);
                ps.setNull(4, Types.INTEGER); // page_number: not tracked for archive-derived text
                ps.setString(5, contents.get(i));
                ps.setString(6, toVectorString(embeddings.get(i)));
                ps.setInt(7, tokenCounts.get(i));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Creates the whole-run {@code ingestion_jobs} row (status {@code QUEUED}). */
    public UUID createJob(Connection conn, int total) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO ingestion_jobs (id, status, total, processed, failed, created_at, updated_at)
                VALUES (?, 'QUEUED', ?, 0, 0, now(), now())
                """)) {
            ps.setObject(1, id);
            ps.setInt(2, total);
            ps.executeUpdate();
        }
        return id;
    }

    /** Updates the job's lifecycle status ({@code RUNNING}, {@code DONE}, {@code FAILED}). */
    public void updateJobStatus(Connection conn, UUID jobId, String status) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE ingestion_jobs SET status = ?, updated_at = now() WHERE id = ?")) {
            ps.setString(1, status);
            ps.setObject(2, jobId);
            ps.executeUpdate();
        }
    }

    /** Updates the job's running processed/failed counters. */
    public void updateJobProgress(Connection conn, UUID jobId, int processed, int failed) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE ingestion_jobs SET processed = ?, failed = ?, updated_at = now() WHERE id = ?")) {
            ps.setInt(1, processed);
            ps.setInt(2, failed);
            ps.setObject(3, jobId);
            ps.executeUpdate();
        }
    }

    /** Count of {@code document_sources} rows in the target DB whose status isn't {@code DONE}. */
    public int countNotDone(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM document_sources WHERE status != 'DONE'")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Total count of {@code document_sources} rows in the target DB. */
    public int countAll(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM document_sources")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private String toVectorString(List<Float> embedding) {
        return embedding.stream()
                .map(Object::toString)
                .collect(Collectors.joining(",", "[", "]"));
    }
}
