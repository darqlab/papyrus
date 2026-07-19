package io.darqlab.papyrus.api.service;

import io.darqlab.papyrus.core.service.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Startup guard for reingest task P2.3 ("Guard" step of the model-switch IP, Step 6):
 * refuses to start the application if the configured embedding provider's dimension
 * disagrees with the live database's actual {@code document_chunks.embedding} column
 * dimension.
 *
 * <p>Why this matters: per ADR-009, each embedding model gets its own database
 * ({@code papyrus_<model>}, e.g. {@code papyrus_voyage3lite} = 512 dims,
 * {@code papyrus_openai3small} = 1536, {@code papyrus_nomic} = 768). If
 * {@code DATABASE_URL} is ever pointed at the wrong per-model database — or
 * {@code papyrus.embedding.provider}/{@code papyrus.embedding.voyage.model} is changed
 * without a matching database — writes would silently attempt to insert a
 * wrong-dimension vector. Failing fast at startup is far safer than discovering this at
 * write time (or worse, having Postgres/pgvector reject or truncate the write in a
 * confusing way deep inside an ingestion job).
 *
 * <p>Deliberately queries the <em>actual live column</em> via
 * {@code pg_attribute.atttypmod} rather than reusing {@link EmbeddingModelInfoService}'s
 * database-name-based reverse lookup — a database could be misnamed, or the migration
 * history could be stale, so the real column definition is the more authoritative source
 * of truth here.
 *
 * <p>Runs as an {@link ApplicationRunner}, which executes after the application context
 * has fully refreshed — i.e. after Spring Boot's Flyway auto-configuration
 * ({@code FlywayMigrationInitializer}, an {@code InitializingBean}) has already applied
 * migrations during context refresh. This ordering is relied upon so the guard only ever
 * runs against a fully migrated schema; see {@code EmbeddingDimensionGuardTest} for a test
 * that exercises this against a real Postgres+pgvector instance rather than assuming it.
 */
@Component
public class EmbeddingDimensionGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingDimensionGuard.class);

    private static final String COLUMN_DIMENSION_SQL =
            "SELECT atttypmod FROM pg_attribute "
                    + "WHERE attrelid = 'document_chunks'::regclass AND attname = 'embedding'";

    private final EmbeddingService embeddingService;
    private final DataSource dataSource;

    public EmbeddingDimensionGuard(EmbeddingService embeddingService, DataSource dataSource) {
        this.embeddingService = embeddingService;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        int configuredDimensions = embeddingService.getDimensions();
        Optional<Integer> liveDimensions = readLiveColumnDimension();

        if (liveDimensions.isEmpty()) {
            // Expected only on a brand-new database before Flyway has ever run (e.g. a
            // manual query against a not-yet-bootstrapped DB, or Flyway disabled). Since
            // FlywayMigrationInitializer runs during context refresh — strictly before
            // ApplicationRunner beans — this should not happen in the normal boot path.
            // Warn rather than crash: there is nothing meaningfully wrong to guard against
            // if the table doesn't exist yet, and refusing to start here would block the
            // very migration that would fix it.
            log.warn("Embedding dimension guard: document_chunks.embedding column was not found "
                    + "(expected only on a database that Flyway has not migrated yet); "
                    + "skipping the startup dimension check.");
            return;
        }

        int liveDimension = liveDimensions.get();
        if (liveDimension != configuredDimensions) {
            String databaseName = currentDatabaseName();
            throw new IllegalStateException(
                    "Embedding dimension mismatch at startup: the configured embedding provider "
                            + "expects dimension " + configuredDimensions + ", but the live database '"
                            + databaseName + "' has document_chunks.embedding declared as vector("
                            + liveDimension + "). Refusing to start rather than risk writing "
                            + "wrong-dimension vectors. Likely causes: DATABASE_URL is pointed at the "
                            + "wrong per-model database (see ADR-009's papyrus_<model> naming "
                            + "convention), or papyrus.embedding.provider / "
                            + "papyrus.embedding.voyage.model does not match the model that database "
                            + "was built for. Fix by pointing DATABASE_URL at the papyrus_<model> "
                            + "database matching the configured provider, or by updating "
                            + "papyrus.embedding.provider to match the live database's model.");
        }

        log.info("Embedding dimension guard: configured provider dimension ({}) matches live "
                + "database '{}' column vector({}).", configuredDimensions, currentDatabaseName(), liveDimension);
    }

    private Optional<Integer> readLiveColumnDimension() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(COLUMN_DIMENSION_SQL);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(rs.getInt(1));
        } catch (SQLException e) {
            log.warn("Embedding dimension guard: could not read document_chunks.embedding column "
                    + "definition (table/column not found yet, or query failed: {}); skipping the "
                    + "startup dimension check.", e.getMessage());
            return Optional.empty();
        }
    }

    private String currentDatabaseName() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.getCatalog();
        } catch (SQLException e) {
            return "unknown";
        }
    }
}
