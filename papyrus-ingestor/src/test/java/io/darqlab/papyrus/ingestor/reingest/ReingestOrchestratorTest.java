package io.darqlab.papyrus.ingestor.reingest;

import io.darqlab.papyrus.core.domain.IngestionStatus;
import io.darqlab.papyrus.core.service.EmbeddingService;
import io.darqlab.papyrus.ingestor.config.IngestorProperties;
import io.darqlab.papyrus.ingestor.model.TargetModelSpec;
import io.darqlab.papyrus.pipeline.archive.ArchiveService;
import io.darqlab.papyrus.pipeline.chunking.ChunkingService;
import io.darqlab.papyrus.pipeline.config.ChunkingStrategy;
import io.darqlab.papyrus.pipeline.config.PapyrusProperties;
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
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Exercises {@link ReingestOrchestrator}'s control flow — enumeration, per-source
 * chunk+embed+write, resumability (skip-DONE / drop-and-rebuild), continue-on-failure, and
 * the ingestion_jobs/readiness bookkeeping — against a real target Postgres+pgvector database
 * (schema applied via the same Flyway migrations {@code DatabaseBootstrapper} uses at runtime).
 *
 * <p>{@link SourceEnumerator} and {@link ArchiveService} are mocked: the orchestrator only
 * needs <em>a</em> JDBC connection to open successfully for the "source" side (there is
 * nothing to actually read from it once enumeration is mocked), and archive I/O is
 * filesystem-based and orthogonal to the DB-orchestration behavior under test here. A real
 * {@link ChunkingService} + a deterministic fake {@link EmbeddingService} are used so that
 * chunk counts / token counts / embedding dimensions in the target DB reflect real logic.
 */
@Testcontainers
class ReingestOrchestratorTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    private static Connection assertionConn;

    private TargetDbWriter writer;
    private ArchiveService archiveService;
    private EmbeddingService embeddingService;
    private ChunkingService chunkingService;

    @BeforeAll
    static void migrateSchema() throws Exception {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        assertionConn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    @AfterAll
    static void closeConnection() throws Exception {
        assertionConn.close();
    }

    @BeforeEach
    void setUp() throws Exception {
        try (Statement st = assertionConn.createStatement()) {
            st.execute("TRUNCATE document_chunks, document_sources, ingestion_jobs");
        }
        writer = new TargetDbWriter();
        archiveService = mock(ArchiveService.class);
        embeddingService = mock(EmbeddingService.class);
        when(embeddingService.embed(any())).thenReturn(basisVector(0));
        chunkingService = new ChunkingService(testChunkingProperties(), embeddingService);
    }

    private ReingestOrchestrator orchestratorWithSources(List<SourceRow> sources) throws Exception {
        SourceEnumerator enumerator = mock(SourceEnumerator.class);
        when(enumerator.enumerate(any())).thenReturn(sources);
        return new ReingestOrchestrator(testProperties(), enumerator, writer);
    }

    private ReingestOrchestrator orchestratorWithSourcesAndLimit(List<SourceRow> sources, int limit) throws Exception {
        SourceEnumerator enumerator = mock(SourceEnumerator.class);
        when(enumerator.enumerate(any())).thenReturn(sources);
        return new ReingestOrchestrator(testProperties(limit), enumerator, writer);
    }

    @Test
    void run_freshSources_writesDoneSourcesAndChunksAndReadyJob() throws Exception {
        when(archiveService.readExtractedText(any(), any())).thenReturn("Paragraph one.\n\nParagraph two.");

        SourceRow row = sourceRow("doc-a.pdf", "2024-01-01_doc-a", null);
        ReingestOrchestrator orchestrator = orchestratorWithSources(List.of(row));

        orchestrator.run(spec(), archiveService, embeddingService, chunkingService);

        assertEquals(1, writer.countAll(assertionConn));
        assertEquals(0, writer.countNotDone(assertionConn), "the single source should have completed DONE");
        assertEquals("DONE", writer.findStatus(assertionConn, row.id()).orElseThrow());

        try (Statement st = assertionConn.createStatement()) {
            var rs = st.executeQuery(
                    "SELECT COUNT(*) FROM document_chunks WHERE source_id = '" + row.id() + "'");
            rs.next();
            assertTrue(rs.getInt(1) > 0, "chunks should have been written for the source");
        }

        try (Statement st = assertionConn.createStatement()) {
            var rs = st.executeQuery("SELECT status, total, processed, failed FROM ingestion_jobs");
            assertTrue(rs.next());
            assertEquals("DONE", rs.getString("status"));
            assertEquals(1, rs.getInt("total"));
            assertEquals(1, rs.getInt("processed"));
            assertEquals(0, rs.getInt("failed"));
        }
    }

    @Test
    void run_secondRun_skipsAlreadyDoneSourceWithoutDuplicating() throws Exception {
        when(archiveService.readExtractedText(any(), any())).thenReturn("Some archived text.");
        SourceRow row = sourceRow("doc-b.pdf", "2024-01-02_doc-b", null);

        orchestratorWithSources(List.of(row)).run(spec(), archiveService, embeddingService, chunkingService);
        assertEquals("DONE", writer.findStatus(assertionConn, row.id()).orElseThrow());

        // Second run over the same (still-DONE) source must not create a duplicate row.
        orchestratorWithSources(List.of(row)).run(spec(), archiveService, embeddingService, chunkingService);

        assertEquals(1, writer.countAll(assertionConn), "re-running a DONE source must not duplicate its row");
        assertEquals("DONE", writer.findStatus(assertionConn, row.id()).orElseThrow());

        // readExtractedText / embed should only ever have been invoked for the first run's source.
        verify(archiveService, times(1)).readExtractedText(any(), any());
    }

    @Test
    void run_withSourceLimit_capsToFirstNSourcesInEnumerationOrder() throws Exception {
        when(archiveService.readExtractedText(any(), any())).thenReturn("Some archived text.");

        SourceRow first = sourceRow("doc-f.pdf", "2024-01-06_doc-f", null);
        SourceRow second = sourceRow("doc-g.pdf", "2024-01-07_doc-g", null);
        SourceRow third = sourceRow("doc-h.pdf", "2024-01-08_doc-h", null);

        orchestratorWithSourcesAndLimit(List.of(first, second, third), 1)
                .run(spec(), archiveService, embeddingService, chunkingService);

        assertEquals(1, writer.countAll(assertionConn), "only the first source (per the limit) should have a row");
        assertEquals("DONE", writer.findStatus(assertionConn, first.id()).orElseThrow());
        assertTrue(writer.findStatus(assertionConn, second.id()).isEmpty(), "second source should not have been processed");
        assertTrue(writer.findStatus(assertionConn, third.id()).isEmpty(), "third source should not have been processed");

        try (Statement st = assertionConn.createStatement()) {
            var rs = st.executeQuery("SELECT total FROM ingestion_jobs");
            assertTrue(rs.next());
            assertEquals(1, rs.getInt("total"), "job total should reflect the capped count, not the full enumeration");
        }
    }

    @Test
    void run_staleFailedRow_isDroppedAndRebuilt() throws Exception {
        SourceRow row = sourceRow("doc-c.pdf", "2024-01-03_doc-c", null);

        // Seed a stale FAILED row directly (simulating a prior crashed/failed run), reusing the
        // same id the orchestrator will reuse (ID reuse — see ReingestOrchestrator class javadoc).
        writer.insertSource(assertionConn, row.id(), row, IngestionStatus.FAILED);
        writer.updateSourceStatus(assertionConn, row.id(), IngestionStatus.FAILED, "previous crash");

        when(archiveService.readExtractedText(any(), any())).thenReturn("Fresh archived text.");
        orchestratorWithSources(List.of(row)).run(spec(), archiveService, embeddingService, chunkingService);

        assertEquals(1, writer.countAll(assertionConn), "stale row should be replaced, not duplicated");
        assertEquals("DONE", writer.findStatus(assertionConn, row.id()).orElseThrow());
    }

    @Test
    void run_oneSourceFailsMissingArchive_continuesAndMarksOnlyThatOneFailed() throws Exception {
        SourceRow good = sourceRow("doc-d.pdf", "2024-01-04_doc-d", null);
        SourceRow missing = sourceRow("doc-e.pdf", "2024-01-05_doc-e", null);

        when(archiveService.readExtractedText(any(), eq("2024-01-04_doc-d"))).thenReturn("Good archived text.");
        when(archiveService.readExtractedText(any(), eq("2024-01-05_doc-e"))).thenReturn(null);

        orchestratorWithSources(List.of(good, missing))
                .run(spec(), archiveService, embeddingService, chunkingService);

        assertEquals(2, writer.countAll(assertionConn), "both sources should have a row, even the failed one");
        assertEquals("DONE", writer.findStatus(assertionConn, good.id()).orElseThrow());
        assertEquals("FAILED", writer.findStatus(assertionConn, missing.id()).orElseThrow());

        try (Statement st = assertionConn.createStatement()) {
            // processed/failed are disjoint counters (processed = succeeded, whether freshly
            // done or skipped-as-already-done); the one archive-missing source counts as failed.
            var rs = st.executeQuery("SELECT processed, failed FROM ingestion_jobs");
            assertTrue(rs.next());
            assertEquals(1, rs.getInt("processed"));
            assertEquals(1, rs.getInt("failed"));
        }
    }

    @Test
    void run_sharedArchiveOwnerInSameBatch_backfillsArchiveSourceId() throws Exception {
        // "dependent" points its archive_source_id at "owner" (a prior re-ingest sharing the
        // same physical archive directory) — both are reingested together in one batch.
        SourceRow owner = sourceRow("owner.pdf", "2024-01-06_owner", null);
        SourceRow dependent = sourceRow("dependent.pdf", "2024-01-06_owner", owner.id());

        when(archiveService.readExtractedText(any(), eq("2024-01-06_owner"))).thenReturn("Shared archived text.");

        // Enumeration order deliberately puts the dependent BEFORE its owner, to prove the
        // backfill pass (run after the whole loop) doesn't depend on insert order.
        orchestratorWithSources(List.of(dependent, owner))
                .run(spec(), archiveService, embeddingService, chunkingService);

        assertEquals("DONE", writer.findStatus(assertionConn, owner.id()).orElseThrow());
        assertEquals("DONE", writer.findStatus(assertionConn, dependent.id()).orElseThrow());

        try (Statement st = assertionConn.createStatement()) {
            var rs = st.executeQuery(
                    "SELECT archive_source_id FROM document_sources WHERE id = '" + dependent.id() + "'");
            assertTrue(rs.next());
            assertEquals(owner.id(), rs.getObject("archive_source_id", UUID.class));
        }
    }

    @Test
    void run_sharedArchiveOwnerNotInBatch_leavesArchiveSourceIdNullWithoutFailingSource() throws Exception {
        // The owner id here belongs to no row in the target DB at all (its own source was never
        // archived / never part of this batch) — the backfill for "dependent" must fail softly.
        UUID absentOwnerId = UUID.randomUUID();
        SourceRow dependent = sourceRow("orphan-dependent.pdf", "2024-01-07_orphan", absentOwnerId);

        when(archiveService.readExtractedText(any(), eq("2024-01-07_orphan"))).thenReturn("Orphaned text.");

        orchestratorWithSources(List.of(dependent)).run(spec(), archiveService, embeddingService, chunkingService);

        // The source itself still completes successfully...
        assertEquals("DONE", writer.findStatus(assertionConn, dependent.id()).orElseThrow());
        // ...but its archive_source_id could not be backfilled, so it stays NULL.
        try (Statement st = assertionConn.createStatement()) {
            var rs = st.executeQuery(
                    "SELECT archive_source_id FROM document_sources WHERE id = '" + dependent.id() + "'");
            assertTrue(rs.next());
            assertNull(rs.getObject("archive_source_id"));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private TargetModelSpec spec() {
        return new TargetModelSpec("voyage-3-lite", "voyage", 512, postgres.getDatabaseName(), true);
    }

    private IngestorProperties testProperties() {
        // Both "source" and "target" JDBC URLs point at the same testcontainer database —
        // SourceEnumerator is mocked, so the source connection is opened but never queried
        // for real content; only the target-database name/credentials matter for TargetDbWriter.
        IngestorProperties.Db db = new IngestorProperties.Db(
                postgres.getHost(), postgres.getFirstMappedPort(), postgres.getDatabaseName(),
                postgres.getUsername(), postgres.getPassword(), postgres.getDatabaseName());
        return new IngestorProperties("voyage-3-lite", db, "/unused-in-this-test", null, null);
    }

    private IngestorProperties testProperties(int sourceLimit) {
        IngestorProperties.Db db = new IngestorProperties.Db(
                postgres.getHost(), postgres.getFirstMappedPort(), postgres.getDatabaseName(),
                postgres.getUsername(), postgres.getPassword(), postgres.getDatabaseName());
        return new IngestorProperties("voyage-3-lite", db, "/unused-in-this-test", null, sourceLimit);
    }

    private SourceRow sourceRow(String filename, String archiveFilename, UUID archiveSourceId) {
        return new SourceRow(UUID.randomUUID(), filename, "application/pdf",
                null, null, null, archiveFilename, archiveSourceId);
    }

    private PapyrusProperties testChunkingProperties() {
        PapyrusProperties.SemanticChunkingProperties semantic =
                new PapyrusProperties.SemanticChunkingProperties(0.75f, 3);
        PapyrusProperties.SectionChunkingProperties section =
                new PapyrusProperties.SectionChunkingProperties("^\\d{3}(\\.\\d{2,3})+\\s+\\S", 50);
        PapyrusProperties.ChunkingProperties chunkingProps =
                new PapyrusProperties.ChunkingProperties(ChunkingStrategy.PARAGRAPH, 512, 64, semantic, section);
        return new PapyrusProperties(null, chunkingProps, null, null, null, null);
    }

    private List<Float> basisVector(int hotIndex) {
        Float[] vec = new Float[512];
        for (int i = 0; i < 512; i++) vec[i] = i == hotIndex ? 1.0f : 0.0f;
        return List.of(vec);
    }
}
