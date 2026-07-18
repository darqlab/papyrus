package io.darqlab.papyrus.ingestor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the ingestor CLI, bound from {@code application.yml} (which in turn
 * reads the env vars documented in the reingest IP / ADR-009).
 *
 * <p>Deliberately separate from {@code papyrus-pipeline}'s {@code PapyrusProperties} —
 * this module does not scan or reuse that bean in this task; DB admin credentials here
 * are intentionally distinct from the serving app's {@code DATABASE_USER}/
 * {@code DATABASE_PASSWORD} (this tool needs CREATE DATABASE privilege, the serving app
 * does not).
 *
 * @param targetModel  {@code TARGET_MODEL} env var, e.g. {@code voyage-3-lite}
 * @param db           Postgres server connection + admin credentials
 * @param archivePath  {@code ARCHIVE_PATH} — filesystem archive volume mount, read by the
 *                     P1.3 {@code ReingestOrchestrator} loop via {@code ArchiveService}
 * @param voyage       Voyage AI embedding credentials, used to instantiate a standalone
 *                     {@code VoyageAiEmbeddingService} (P1.3) — deliberately separate from
 *                     papyrus-pipeline's {@code PapyrusProperties.EmbeddingProperties} since
 *                     this module doesn't scan that package; same underlying env vars
 *                     ({@code VOYAGE_API_KEY} / {@code VOYAGE_MODEL}) as the serving app.
 */
@ConfigurationProperties(prefix = "ingestor")
public record IngestorProperties(
        String targetModel,
        Db db,
        String archivePath,
        Voyage voyage
) {

    /**
     * @param host            Postgres host (e.g. {@code papyrus-pgvector}, the compose service name)
     * @param port            Postgres port
     * @param adminDatabase   the server's default/admin database to connect to for
     *                        {@code CREATE DATABASE} / {@code pg_database} checks (typically {@code postgres})
     * @param adminUser       {@code INGESTOR_DB_ADMIN_USER} — must have CREATE DATABASE privilege
     * @param adminPassword   {@code INGESTOR_DB_ADMIN_PASSWORD}
     * @param sourceDatabase  {@code INGESTOR_SOURCE_DATABASE} — the live/serving app's database
     *                        name (same Postgres server, reuses the admin credentials above),
     *                        read-only enumerated by the P1.3 reingest loop to discover
     *                        {@code document_sources} rows with an archived file. Default
     *                        {@code papyrus}, matching {@code ${POSTGRES_DB:-papyrus}} in
     *                        {@code docker-compose.yml}.
     */
    public record Db(
            String host,
            int port,
            String adminDatabase,
            String adminUser,
            String adminPassword,
            String sourceDatabase
    ) {
    }

    /**
     * @param apiKey {@code VOYAGE_API_KEY}
     * @param model  {@code VOYAGE_MODEL}, e.g. {@code voyage-3-lite}
     */
    public record Voyage(
            String apiKey,
            String model
    ) {
    }
}
