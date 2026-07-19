package io.darqlab.papyrus.ingestor.db;

import io.darqlab.papyrus.ingestor.config.IngestorProperties;
import io.darqlab.papyrus.ingestor.model.TargetModelSpec;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;

/**
 * Creates (idempotently) and schema-bootstraps the per-model "blue-green" database
 * described in ADR-009 / reingest-model-switch-ip.md Step 2.
 *
 * <p>Deliberately uses plain JDBC ({@link DriverManager}) rather than a Spring-managed
 * {@code DataSource}: this class needs to open connections to <em>two different</em>
 * databases on the same Postgres server (the admin/catalog database to run
 * {@code CREATE DATABASE}, then the newly-created target database to apply schema) which
 * doesn't fit Spring Boot's single-DataSource-per-app-context model. The ingestor's
 * {@code IngestorApplication} therefore also excludes Spring's DataSource/JPA
 * autoconfiguration (see its class javadoc) so no {@code spring.datasource.*} needs
 * to be configured.
 *
 * <h2>Vector-dimension / migration wrinkle (architectural note)</h2>
 * {@code V1__create_schema.sql} (reused unmodified from papyrus-pipeline via
 * {@code classpath:db/migration}) hardcodes {@code embedding vector(512)}. That is
 * correct for {@code voyage-3-lite} (dim 512) but would be <b>wrong</b> for any future
 * model with a different dimension (OpenAI text-embedding-3-small = 1536, Ollama
 * nomic-embed-text = 768).
 *
 * <p><b>Chosen approach (option "a" from the task brief):</b> run Flyway unmodified
 * against the target database and rely on {@link io.darqlab.papyrus.ingestor.TargetModelResolver}
 * to refuse any model without a working {@code EmbeddingService} — today that means only
 * {@code voyage-3-lite} (512) can ever reach this code path, so the hardcoded 512 is
 * always correct in practice. This was chosen over templating the DDL by dimension
 * because it is zero-risk for the only model this task needs to support, and avoids
 * inventing an untested parameterized-schema mechanism speculatively.
 *
 * <p><b>Real gap for future work:</b> the day a second provider's
 * {@code embeddingServiceAvailable} flag flips to {@code true} with a non-512 dimension,
 * {@code V1__create_schema.sql} must be templated (e.g. {@code vector(${dim})} applied
 * via a programmatic DDL step instead of a static Flyway script, or a per-dimension
 * migration variant) <em>before</em> that model can be targeted — this class only warns,
 * it does not yet handle that case correctly.
 */
@Component
public class DatabaseBootstrapper {

    private static final Logger log = LoggerFactory.getLogger(DatabaseBootstrapper.class);

    /** Postgres identifiers here always come from {@link TargetModelSpec}, our own
     *  hardcoded naming-contract table — never user input — but validated anyway. */
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]*");

    private final IngestorProperties properties;

    public DatabaseBootstrapper(IngestorProperties properties) {
        this.properties = properties;
    }

    /**
     * Ensures {@code papyrus_<model>} exists on the Postgres server, creating it if
     * necessary. Idempotent: checks {@code pg_database} first (Postgres has no native
     * {@code CREATE DATABASE IF NOT EXISTS}) and skips creation if already present.
     */
    public void ensureDatabase(TargetModelSpec spec) throws SQLException {
        String dbName = validatedIdentifier(spec.databaseName());
        String adminUrl = jdbcUrl(properties.db().adminDatabase());

        log.info("Connecting to admin database '{}' at {}:{} to check/create '{}'.",
                properties.db().adminDatabase(), properties.db().host(), properties.db().port(), dbName);

        try (Connection conn = DriverManager.getConnection(
                adminUrl, properties.db().adminUser(), properties.db().adminPassword())) {

            if (databaseExists(conn, dbName)) {
                log.info("Database '{}' already exists — skipping creation (idempotent).", dbName);
                return;
            }

            log.info("Database '{}' does not exist — creating.", dbName);
            try (Statement st = conn.createStatement()) {
                // CREATE DATABASE cannot be parameterized or run inside a transaction block.
                st.executeUpdate("CREATE DATABASE " + dbName);
            }
            log.info("Database '{}' created.", dbName);
        }
    }

    private boolean databaseExists(Connection conn, String dbName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
            ps.setString(1, dbName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Applies the same Flyway migrations papyrus-pipeline uses ({@code V1..V5} at time of
     * writing) to the target database. See class javadoc for the dimension wrinkle.
     */
    public void applySchema(TargetModelSpec spec) {
        String dbName = validatedIdentifier(spec.databaseName());
        String targetUrl = jdbcUrl(dbName);

        if (spec.dimensions() != 512) {
            log.warn("Target model '{}' has dimension {} (!= 512). V1__create_schema.sql hardcodes "
                            + "vector(512) and will be applied UNMODIFIED — this WILL produce a wrong-dimension "
                            + "column for this model. See DatabaseBootstrapper's class javadoc for the "
                            + "parameterization work needed before this model can safely be targeted.",
                    spec.modelName(), spec.dimensions());
        }

        log.info("Applying schema (Flyway, migrations reused from papyrus-pipeline's classpath:db/migration) "
                + "to database '{}'.", dbName);

        Flyway flyway = Flyway.configure()
                .dataSource(targetUrl, properties.db().adminUser(), properties.db().adminPassword())
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        log.info("Schema applied to '{}'.", dbName);
    }

    private String jdbcUrl(String database) {
        return "jdbc:postgresql://%s:%d/%s".formatted(
                properties.db().host(), properties.db().port(), database);
    }

    private String validatedIdentifier(String name) {
        if (!SAFE_IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Refusing to use unsafe database identifier '" + name + "' (expected lower_snake_case).");
        }
        return name;
    }
}
