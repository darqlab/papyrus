package io.darqlab.papyrus.ingestor.reingest;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@link SourceEnumerator} only returns {@code document_sources} rows with a
 * non-null {@code archive_filename} (the population {@link ReingestOrchestrator} can rebuild),
 * and correctly carries through the per-source chunking overrides and archive identity columns.
 */
@Testcontainers
class SourceEnumeratorTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    private static Connection conn;

    private final SourceEnumerator enumerator = new SourceEnumerator();

    @BeforeAll
    static void migrateSchema() throws Exception {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    @AfterAll
    static void closeConnection() throws Exception {
        conn.close();
    }

    @BeforeEach
    void cleanTables() throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute("TRUNCATE document_chunks, document_sources, ingestion_jobs");
        }
    }

    @Test
    void enumerate_excludesSourcesWithoutArchiveFilename() throws Exception {
        insertSource("archived.pdf", "APPLICATION/PDF", "2024-01-01_archived", null, null, null);
        insertSource("not-archived.pdf", "application/pdf", null, null, null, null);

        List<SourceRow> rows = enumerator.enumerate(conn);

        assertEquals(1, rows.size());
        assertEquals("archived.pdf", rows.get(0).filename());
    }

    @Test
    void enumerate_carriesThroughChunkingOverridesAndArchiveSourceId() throws Exception {
        // archive_source_id has an FK to document_sources(id) — insert the "owner" row
        // (the original source whose archive directory this one shares) first.
        UUID archiveOwner = insertOwnerSource("original.pdf");
        insertSource("shared-archive.pdf", "application/pdf", "2024-02-02_page",
                "SEMANTIC", 256, 32, archiveOwner);

        List<SourceRow> rows = enumerator.enumerate(conn);

        assertEquals(1, rows.size());
        SourceRow row = rows.get(0);
        assertEquals("SEMANTIC", row.chunkingStrategy());
        assertEquals(256, row.chunkingMaxTokens());
        assertEquals(32, row.chunkingOverlapTokens());
        assertEquals(archiveOwner, row.archiveSourceId());
        assertEquals(archiveOwner, row.archiveDirId());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Inserts a minimal row (no archive_filename — excluded from enumerate()) to satisfy the
     *  archive_source_id FK when a test needs an "owner" row to point at. */
    private UUID insertOwnerSource(String filename) throws Exception {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO document_sources (id, filename, content_type, status, created_at, updated_at)
                VALUES (?, ?, 'application/pdf', 'DONE', now(), now())
                """)) {
            ps.setObject(1, id);
            ps.setString(2, filename);
            ps.executeUpdate();
        }
        return id;
    }

    private void insertSource(String filename, String contentType, String archiveFilename,
                               String chunkingStrategy, Integer maxTokens, Integer overlapTokens) throws Exception {
        insertSource(filename, contentType, archiveFilename, chunkingStrategy, maxTokens, overlapTokens, null);
    }

    private void insertSource(String filename, String contentType, String archiveFilename,
                               String chunkingStrategy, Integer maxTokens, Integer overlapTokens,
                               UUID archiveSourceId) throws Exception {
        String sql = """
                INSERT INTO document_sources
                    (id, filename, content_type, status, archive_filename, chunking_strategy,
                     chunking_max_tokens, chunking_overlap_tokens, archive_source_id, created_at, updated_at)
                VALUES (?, ?, ?, 'DONE', ?, ?, ?, ?, ?, now(), now())
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, filename);
            ps.setString(3, contentType);
            ps.setString(4, archiveFilename);
            ps.setString(5, chunkingStrategy);
            if (maxTokens == null) ps.setNull(6, java.sql.Types.INTEGER); else ps.setInt(6, maxTokens);
            if (overlapTokens == null) ps.setNull(7, java.sql.Types.INTEGER); else ps.setInt(7, overlapTokens);
            ps.setObject(8, archiveSourceId);
            ps.executeUpdate();
        }
    }
}
