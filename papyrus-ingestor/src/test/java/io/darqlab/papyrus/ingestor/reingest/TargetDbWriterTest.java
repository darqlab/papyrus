package io.darqlab.papyrus.ingestor.reingest;

import io.darqlab.papyrus.core.domain.IngestionStatus;
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
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link TargetDbWriter} against a real Postgres+pgvector instance (schema applied
 * once via the same Flyway migrations {@link io.darqlab.papyrus.ingestor.db.DatabaseBootstrapper}
 * applies at runtime), since it is plain hand-rolled JDBC — worth verifying against the real
 * column types and constraints (uuid, vector, the {@code archive_source_id} same-DB FK) rather
 * than mocking {@link Connection}.
 */
@Testcontainers
class TargetDbWriterTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    private static Connection conn;

    private final TargetDbWriter writer = new TargetDbWriter();

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
    void insertSource_thenFindStatus_returnsItsStatus() throws Exception {
        SourceRow row = row("a.txt", null);

        writer.insertSource(conn, row.id(), row, IngestionStatus.PROCESSING);

        Optional<String> found = writer.findStatus(conn, row.id());
        assertTrue(found.isPresent());
        assertEquals("PROCESSING", found.get());
    }

    @Test
    void insertSource_alwaysInsertsNullArchiveSourceId_regardlessOfRowValue() throws Exception {
        // archive_source_id is deliberately deferred to updateArchiveSourceId (see class javadoc
        // "ID reuse" note) — insertSource must never write it directly, to avoid an FK failure
        // when the referenced owner row doesn't exist yet.
        SourceRow row = row("shared.txt", UUID.randomUUID());

        writer.insertSource(conn, row.id(), row, IngestionStatus.PROCESSING);

        try (Statement st = conn.createStatement()) {
            var rs = st.executeQuery(
                    "SELECT archive_source_id FROM document_sources WHERE id = '" + row.id() + "'");
            assertTrue(rs.next());
            assertNull(rs.getObject("archive_source_id"));
        }
    }

    @Test
    void findStatus_noMatch_returnsEmpty() throws Exception {
        assertTrue(writer.findStatus(conn, UUID.randomUUID()).isEmpty());
    }

    @Test
    void updateSourceStatus_persistsStatusAndError() throws Exception {
        SourceRow row = row("b.txt", null);
        writer.insertSource(conn, row.id(), row, IngestionStatus.PROCESSING);

        writer.updateSourceStatus(conn, row.id(), IngestionStatus.FAILED, "boom");

        assertEquals("FAILED", writer.findStatus(conn, row.id()).orElseThrow());
        try (Statement st = conn.createStatement()) {
            var rs = st.executeQuery("SELECT error FROM document_sources WHERE id = '" + row.id() + "'");
            assertTrue(rs.next());
            assertEquals("boom", rs.getString("error"));
        }
    }

    @Test
    void updateArchiveSourceId_succeedsWhenOwnerRowExists() throws Exception {
        SourceRow owner = row("owner.txt", null);
        writer.insertSource(conn, owner.id(), owner, IngestionStatus.DONE);

        SourceRow dependent = row("dependent.txt", owner.id());
        writer.insertSource(conn, dependent.id(), dependent, IngestionStatus.PROCESSING);

        writer.updateArchiveSourceId(conn, dependent.id(), owner.id());

        try (Statement st = conn.createStatement()) {
            var rs = st.executeQuery(
                    "SELECT archive_source_id FROM document_sources WHERE id = '" + dependent.id() + "'");
            assertTrue(rs.next());
            assertEquals(owner.id(), rs.getObject("archive_source_id", UUID.class));
        }
    }

    @Test
    void updateArchiveSourceId_throwsWhenOwnerRowAbsent() throws Exception {
        SourceRow dependent = row("orphaned.txt", UUID.randomUUID());
        writer.insertSource(conn, dependent.id(), dependent, IngestionStatus.PROCESSING);

        assertThrows(SQLException.class,
                () -> writer.updateArchiveSourceId(conn, dependent.id(), UUID.randomUUID()));
    }

    @Test
    void deleteSource_cascadesToChunks() throws Exception {
        SourceRow row = row("c.txt", null);
        writer.insertSource(conn, row.id(), row, IngestionStatus.PROCESSING);
        writer.storeChunks(conn, row.id(), List.of("chunk one"), List.of(basisVector(0)), List.of(3));

        writer.deleteSource(conn, row.id());

        assertTrue(writer.findStatus(conn, row.id()).isEmpty());
        try (Statement st = conn.createStatement()) {
            var rs = st.executeQuery("SELECT COUNT(*) FROM document_chunks WHERE source_id = '" + row.id() + "'");
            rs.next();
            assertEquals(0, rs.getInt(1), "chunks should be cascade-deleted with their source");
        }
    }

    @Test
    void storeChunks_persistsAllRowsInOrder() throws Exception {
        SourceRow row = row("d.txt", null);
        writer.insertSource(conn, row.id(), row, IngestionStatus.PROCESSING);

        writer.storeChunks(conn, row.id(),
                List.of("chunk zero", "chunk one"),
                List.of(basisVector(0), basisVector(1)),
                List.of(2, 3));

        try (Statement st = conn.createStatement()) {
            var rs = st.executeQuery(
                    "SELECT chunk_index, content, token_count FROM document_chunks "
                            + "WHERE source_id = '" + row.id() + "' ORDER BY chunk_index");
            assertTrue(rs.next());
            assertEquals(0, rs.getInt("chunk_index"));
            assertEquals("chunk zero", rs.getString("content"));
            assertEquals(2, rs.getInt("token_count"));
            assertTrue(rs.next());
            assertEquals(1, rs.getInt("chunk_index"));
            assertEquals("chunk one", rs.getString("content"));
            assertFalse(rs.next());
        }
    }

    @Test
    void jobLifecycle_createUpdateStatusAndProgress() throws Exception {
        UUID jobId = writer.createJob(conn, 5);
        writer.updateJobStatus(conn, jobId, "RUNNING");
        writer.updateJobProgress(conn, jobId, 3, 1);
        writer.updateJobStatus(conn, jobId, "DONE");

        try (Statement st = conn.createStatement()) {
            var rs = st.executeQuery(
                    "SELECT status, total, processed, failed FROM ingestion_jobs WHERE id = '" + jobId + "'");
            assertTrue(rs.next());
            assertEquals("DONE", rs.getString("status"));
            assertEquals(5, rs.getInt("total"));
            assertEquals(3, rs.getInt("processed"));
            assertEquals(1, rs.getInt("failed"));
        }
    }

    @Test
    void countNotDoneAndCountAll_reflectSourceStatuses() throws Exception {
        writer.insertSource(conn, UUID.randomUUID(), row("e.txt", null), IngestionStatus.DONE);
        writer.insertSource(conn, UUID.randomUUID(), row("f.txt", null), IngestionStatus.PROCESSING);

        assertEquals(2, writer.countAll(conn));
        assertEquals(1, writer.countNotDone(conn));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SourceRow row(String archiveFilename, UUID archiveSourceId) {
        return new SourceRow(UUID.randomUUID(), "orig.txt", "text/plain",
                null, null, null, archiveFilename, archiveSourceId);
    }

    private List<Float> basisVector(int hotIndex) {
        Float[] vec = new Float[512];
        for (int i = 0; i < 512; i++) vec[i] = i == hotIndex ? 1.0f : 0.0f;
        return List.of(vec);
    }
}
