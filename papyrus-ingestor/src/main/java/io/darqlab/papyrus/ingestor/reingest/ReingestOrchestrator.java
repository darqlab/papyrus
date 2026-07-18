package io.darqlab.papyrus.ingestor.reingest;

import io.darqlab.papyrus.core.domain.ExtractedText;
import io.darqlab.papyrus.core.domain.IngestionStatus;
import io.darqlab.papyrus.core.service.EmbeddingService;
import io.darqlab.papyrus.core.util.TokenEstimator;
import io.darqlab.papyrus.ingestor.config.IngestorProperties;
import io.darqlab.papyrus.ingestor.model.TargetModelSpec;
import io.darqlab.papyrus.pipeline.archive.ArchiveService;
import io.darqlab.papyrus.pipeline.chunking.ChunkingConfig;
import io.darqlab.papyrus.pipeline.chunking.ChunkingService;
import io.darqlab.papyrus.pipeline.config.ChunkingStrategy;
import io.darqlab.papyrus.pipeline.config.PapyrusProperties;
import io.darqlab.papyrus.pipeline.embedding.VoyageAiEmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * P1.3: the reingest-from-filesystem-archive batch loop.
 *
 * <p>For each archived source in the <em>live/source</em> database
 * ({@code ingestor.db.source-database}, same Postgres server as the target databases),
 * reads the archived extracted text via {@link ArchiveService}, re-chunks it with
 * {@link ChunkingService}, re-embeds it under the target model with an
 * {@link EmbeddingService}, and writes a fresh {@code document_sources}/{@code document_chunks}
 * row pair into the <em>target</em> per-model database via {@link TargetDbWriter} — mirroring
 * the single-source pattern in {@code DocumentService.reingest(UUID)}, generalized into a
 * batch over every archived source.
 *
 * <h2>Why plain JDBC, not the papyrus-pipeline Spring beans</h2>
 * {@code papyrus-ingestor} excludes Spring's DataSource/JPA autoconfiguration (see
 * {@code IngestorApplication}'s class javadoc) because a single run touches two different
 * Postgres databases — the source/live DB (read-only enumeration) and the freshly bootstrapped
 * target DB (writes). {@link ChunkingService} and {@link VoyageAiEmbeddingService} have no
 * DataSource dependency and are instantiated directly here with a minimal hand-built
 * {@link PapyrusProperties}; {@link ArchiveService} likewise only needs its
 * {@code archive.*} sub-properties. {@code VectorStoreService} (papyrus-pipeline) is
 * <em>not</em> reused because it's bound to Spring's single autoconfigured
 * DataSource/JPA {@code EntityManager} — {@link TargetDbWriter} is its raw-JDBC equivalent,
 * scoped to whichever {@link Connection} the caller passes in.
 *
 * <h2>ID reuse (judgment call — flagged for Gate-2 review)</h2>
 * Each target-DB {@code document_sources} row reuses the <em>source</em> database's own row
 * id verbatim ({@link SourceRow#id()}), rather than generating a fresh UUID as
 * {@code DocumentService.reingest} does for its (single-database) re-ingest. This was
 * necessary, not stylistic: {@code document_sources.archive_source_id} carries a same-database
 * foreign key (V3 migration, {@code REFERENCES document_sources(id)}), but the value being
 * copied into it ({@code SourceRow.archiveSourceId()}) is a <em>source</em>-database id — it
 * only satisfies that FK in the target DB if the row bearing that same id also exists there.
 * Reusing source-DB ids as target-DB ids (safe: fresh per-model database, source ids are
 * already globally-unique UUIDs) makes that hold whenever the referenced owner is also being
 * reingested in the same batch. It also has a bonus: resumability becomes a plain id lookup
 * ({@link TargetDbWriter#findStatus}) instead of matching on {@code archive_filename} +
 * {@code archive_source_id}.
 * {@code archive_source_id} itself is still inserted as {@code NULL} initially (enumeration
 * order could otherwise insert a dependent row before its owner) and backfilled in a dedicated
 * pass after the whole batch ({@link #backfillArchiveSourceIds}) — best-effort: if the owner
 * source has no {@code archive_filename} of its own (so was never a candidate for this batch),
 * that one row's {@code archive_source_id} is left {@code NULL} and a warning is logged; this
 * does not fail the source's own {@code DONE} status, since its {@code archive_filename} alone
 * is enough to re-read its text (see {@link SourceRow#archiveDirId()}).
 *
 * <h2>Resumability (P1.4)</h2>
 * Before inserting a new target-DB source row, an existing row is looked up by id (the reused
 * source-DB id — see above). If that existing row is {@code DONE}, the source is skipped (safe
 * re-run). If it's {@code PROCESSING} or {@code FAILED} (a prior run crashed or failed partway),
 * it is deleted (cascades to its chunks) and reprocessed from scratch — "drop-and-rebuild"
 * rather than leaving duplicates. Readiness is a derived query
 * ({@link TargetDbWriter#countNotDone}), not a stored flag.
 */
@Component
public class ReingestOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ReingestOrchestrator.class);

    // Mirrors papyrus-api's application.yml chunking defaults (CHUNKING_MAX_TOKENS etc.) —
    // hardcoded here rather than re-exposed as ingestor config because per-source overrides
    // (chunking_strategy/max_tokens/overlap_tokens, carried on each SourceRow) are what
    // actually drive re-chunking in practice; these are only the SEMANTIC/SECTION strategy
    // sub-thresholds ChunkingService needs available regardless of per-source strategy.
    private static final float DEFAULT_SEMANTIC_THRESHOLD = 0.75f;
    private static final int DEFAULT_SEMANTIC_MIN_SENTENCES = 3;
    private static final String DEFAULT_SECTION_PATTERN = "^\\d{3}(\\.\\d{2,3})+\\s+\\S";
    private static final int DEFAULT_SECTION_MIN_TOKENS = 50;

    private static final ChunkingStrategy DEFAULT_STRATEGY = ChunkingStrategy.PARAGRAPH;
    private static final int DEFAULT_MAX_TOKENS = 512;
    private static final int DEFAULT_OVERLAP_TOKENS = 64;

    private final IngestorProperties properties;
    private final SourceEnumerator sourceEnumerator;
    private final TargetDbWriter targetDbWriter;

    public ReingestOrchestrator(IngestorProperties properties) {
        this(properties, new SourceEnumerator(), new TargetDbWriter());
    }

    /** Package-visible constructor for testing (inject fakes/mocks for the collaborators). */
    ReingestOrchestrator(IngestorProperties properties, SourceEnumerator sourceEnumerator, TargetDbWriter targetDbWriter) {
        this.properties = properties;
        this.sourceEnumerator = sourceEnumerator;
        this.targetDbWriter = targetDbWriter;
    }

    /**
     * Runs the full reingest loop against the given resolved target model, using the
     * caller-supplied {@link ArchiveService}/{@link EmbeddingService}/{@link ChunkingService}
     * (built once by {@link #run(TargetModelSpec)} via {@link #buildArchiveService()} etc.,
     * or injected directly in tests).
     */
    public void run(TargetModelSpec spec) throws SQLException {
        run(spec, buildArchiveService(), buildEmbeddingService(), null);
    }

    /**
     * Overload used by unit tests to inject a fake {@link ArchiveService}/{@link EmbeddingService}
     * (and, optionally, a pre-built {@link ChunkingService}) instead of the real Voyage/filesystem
     * ones, so the loop's control flow can be exercised without network or disk I/O.
     */
    void run(TargetModelSpec spec, ArchiveService archiveService, EmbeddingService embeddingService,
             ChunkingService chunkingServiceOverride) throws SQLException {

        Instant startedAt = Instant.now();
        ChunkingService chunkingService = chunkingServiceOverride != null
                ? chunkingServiceOverride
                : buildChunkingService(embeddingService);

        String sourceUrl = jdbcUrl(properties.db().sourceDatabase());
        String targetUrl = jdbcUrl(spec.databaseName());

        try (Connection sourceConn = openConnection(sourceUrl);
             Connection targetConn = openConnection(targetUrl)) {

            List<SourceRow> sources = sourceEnumerator.enumerate(sourceConn);
            log.info("Enumerated {} archived source(s) from '{}' to reingest into '{}'.",
                    sources.size(), properties.db().sourceDatabase(), spec.databaseName());

            UUID jobId = targetDbWriter.createJob(targetConn, sources.size());
            targetDbWriter.updateJobStatus(targetConn, jobId, "RUNNING");

            int processed = 0;
            int failed = 0;
            int skipped = 0;
            long totalChunksWritten = 0;
            long totalEmbeddingTokens = 0;

            for (SourceRow row : sources) {
                try {
                    Outcome outcome = processSource(row, archiveService, chunkingService, embeddingService, targetConn);
                    processed++;
                    if (outcome.skipped()) {
                        skipped++;
                    } else {
                        totalChunksWritten += outcome.chunkCount();
                        totalEmbeddingTokens += outcome.tokenCount();
                    }
                } catch (Exception e) {
                    failed++;
                    log.error("Failed to reingest source id={} filename='{}' archiveFilename='{}': {}",
                            row.id(), row.filename(), row.archiveFilename(), e.getMessage(), e);
                }
                targetDbWriter.updateJobProgress(targetConn, jobId, processed, failed);
            }

            targetDbWriter.updateJobStatus(targetConn, jobId, "DONE");

            // Best-effort archive_source_id backfill, run only now that every source that
            // could succeed has a row in the target DB — see class javadoc "ID reuse" note.
            backfillArchiveSourceIds(targetConn, sources);

            int total = targetDbWriter.countAll(targetConn);
            int notDone = targetDbWriter.countNotDone(targetConn);
            boolean ready = notDone == 0;

            if (ready) {
                log.info("Target DB '{}' READY: {}/{} sources DONE.", spec.databaseName(), total, total);
            } else {
                log.warn("Target DB '{}' NOT READY: {} of {} sources not DONE.", spec.databaseName(), notDone, total);
            }

            Duration elapsed = Duration.between(startedAt, Instant.now());
            log.info("=== Reingest summary: sources_enumerated={} (freshly_processed={}, "
                            + "skipped_already_done={}, failed={}), chunks_written={}, "
                            + "embedding_tokens_estimate={}, elapsed={} ===",
                    sources.size(), processed - skipped, skipped, failed,
                    totalChunksWritten, totalEmbeddingTokens, elapsed);
        }
    }

    // ── Per-source processing ────────────────────────────────────────────────

    private Outcome processSource(SourceRow row, ArchiveService archiveService, ChunkingService chunkingService,
                                   EmbeddingService embeddingService, Connection targetConn) throws SQLException {

        // ID reuse (see class javadoc): the target DB row keeps the source DB's own id, so
        // resumability is a plain id lookup — no separate archive-identity key needed.
        UUID targetId = row.id();

        Optional<String> existingStatus = targetDbWriter.findStatus(targetConn, targetId);

        if (existingStatus.isPresent() && IngestionStatus.DONE.name().equals(existingStatus.get())) {
            log.info("Skipping already-DONE source id={} filename='{}'.", targetId, row.filename());
            return Outcome.ofSkipped();
        }

        if (existingStatus.isPresent()) {
            log.info("Dropping stale {} row id={} filename='{}' before reprocessing.",
                    existingStatus.get(), targetId, row.filename());
            targetDbWriter.deleteSource(targetConn, targetId);
        }

        // Write-before-process: insert PROCESSING before any archive read/chunk/embed, so a
        // crash mid-pipeline leaves a visible PROCESSING row (P1.4 resumability signal), and
        // so an archive-read failure below still results in a recorded FAILED row rather than
        // no row at all.
        targetDbWriter.insertSource(targetConn, targetId, row, IngestionStatus.PROCESSING);

        try {
            UUID archiveDirId = row.archiveDirId();
            String extractedText = archiveService.readExtractedText(archiveDirId, row.archiveFilename());
            if (extractedText == null || extractedText.isBlank()) {
                throw new IllegalStateException(
                        "Archived extracted text not found for " + archiveDirId + "/" + row.archiveFilename());
            }

            ExtractedText extracted = ExtractedText.of(extractedText);
            ChunkingConfig config = resolveChunkingConfig(row);
            List<String> chunks = chunkingService.chunk(extracted, config);

            int chunkCount = 0;
            int tokenSum = 0;
            if (!chunks.isEmpty()) {
                List<List<Float>> embeddings = chunks.stream().map(embeddingService::embed).toList();
                List<Integer> tokenCounts = chunks.stream().map(TokenEstimator::estimate).toList();
                targetDbWriter.storeChunks(targetConn, targetId, chunks, embeddings, tokenCounts);
                chunkCount = chunks.size();
                tokenSum = tokenCounts.stream().mapToInt(Integer::intValue).sum();
            }

            targetDbWriter.updateSourceStatus(targetConn, targetId, IngestionStatus.DONE, null);
            return Outcome.ofProcessed(chunkCount, tokenSum);

        } catch (Exception e) {
            targetDbWriter.updateSourceStatus(targetConn, targetId, IngestionStatus.FAILED, e.getMessage());
            throw e;
        }
    }

    /**
     * Best-effort backfill of {@code archive_source_id} for sources whose archive directory
     * is owned by another source (see {@link TargetDbWriter#updateArchiveSourceId}). Run once,
     * after every source in this run has had a chance to get its own row — so ordering within
     * {@code sources} doesn't matter — but a genuinely absent owner (e.g. the owner source has
     * no {@code archive_filename} of its own, so was never a candidate for this batch) simply
     * leaves that row's {@code archive_source_id} {@code NULL}, logged at WARN, never fatal.
     */
    private void backfillArchiveSourceIds(Connection targetConn, List<SourceRow> sources) throws SQLException {
        for (SourceRow row : sources) {
            if (row.archiveSourceId() == null) continue;
            try {
                targetDbWriter.updateArchiveSourceId(targetConn, row.id(), row.archiveSourceId());
            } catch (SQLException e) {
                log.warn("Could not backfill archive_source_id={} for target id={} filename='{}' "
                                + "(owner row likely not present in target DB) — leaving NULL: {}",
                        row.archiveSourceId(), row.id(), row.filename(), e.getMessage());
            }
        }
    }

    private ChunkingConfig resolveChunkingConfig(SourceRow row) {
        ChunkingStrategy strategy = row.chunkingStrategy() != null
                ? ChunkingStrategy.valueOf(row.chunkingStrategy())
                : DEFAULT_STRATEGY;
        int maxTokens = row.chunkingMaxTokens() != null ? row.chunkingMaxTokens() : DEFAULT_MAX_TOKENS;
        int overlapTokens = row.chunkingOverlapTokens() != null ? row.chunkingOverlapTokens() : DEFAULT_OVERLAP_TOKENS;
        return new ChunkingConfig(strategy, maxTokens, overlapTokens);
    }

    // ── Collaborator construction (standalone, no Spring context) ───────────

    private ArchiveService buildArchiveService() {
        PapyrusProperties.ArchiveProperties archiveProps =
                new PapyrusProperties.ArchiveProperties(true, properties.archivePath(), Map.of());
        PapyrusProperties papyrusProperties =
                new PapyrusProperties(null, null, null, null, archiveProps, null);
        return new ArchiveService(papyrusProperties);
    }

    private EmbeddingService buildEmbeddingService() {
        PapyrusProperties.VoyageProperties voyageProps =
                new PapyrusProperties.VoyageProperties(properties.voyage().apiKey(), properties.voyage().model());
        PapyrusProperties.EmbeddingProperties embeddingProps =
                new PapyrusProperties.EmbeddingProperties("voyage", voyageProps, null);
        PapyrusProperties papyrusProperties =
                new PapyrusProperties(embeddingProps, null, null, null, null, null);
        return new VoyageAiEmbeddingService(papyrusProperties);
    }

    private ChunkingService buildChunkingService(EmbeddingService embeddingService) {
        PapyrusProperties.SemanticChunkingProperties semantic =
                new PapyrusProperties.SemanticChunkingProperties(DEFAULT_SEMANTIC_THRESHOLD, DEFAULT_SEMANTIC_MIN_SENTENCES);
        PapyrusProperties.SectionChunkingProperties section =
                new PapyrusProperties.SectionChunkingProperties(DEFAULT_SECTION_PATTERN, DEFAULT_SECTION_MIN_TOKENS);
        PapyrusProperties.ChunkingProperties chunkingProps = new PapyrusProperties.ChunkingProperties(
                DEFAULT_STRATEGY, DEFAULT_MAX_TOKENS, DEFAULT_OVERLAP_TOKENS, semantic, section);
        PapyrusProperties papyrusProperties =
                new PapyrusProperties(null, chunkingProps, null, null, null, null);
        return new ChunkingService(papyrusProperties, embeddingService);
    }

    private Connection openConnection(String url) throws SQLException {
        return DriverManager.getConnection(url, properties.db().adminUser(), properties.db().adminPassword());
    }

    private String jdbcUrl(String database) {
        return "jdbc:postgresql://%s:%d/%s".formatted(properties.db().host(), properties.db().port(), database);
    }

    /** Per-source result, used to accumulate the P1.5 measured-actuals summary. */
    private record Outcome(boolean skipped, int chunkCount, int tokenCount) {
        static Outcome ofSkipped() {
            return new Outcome(true, 0, 0);
        }

        static Outcome ofProcessed(int chunkCount, int tokenCount) {
            return new Outcome(false, chunkCount, tokenCount);
        }
    }
}
