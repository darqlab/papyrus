package io.darqlab.papyrus.api.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises {@link EmbeddingDimensionGuard} against a real Postgres+pgvector instance
 * (same Testcontainers pattern as {@code papyrus-ingestor}'s {@code TargetDbWriterTest}),
 * because the guard's core assumption — that pgvector's {@code pg_attribute.atttypmod}
 * for a {@code vector(N)} column equals {@code N} directly, with no offset — is worth
 * verifying empirically rather than trusting docs blindly.
 *
 * <p>This is the first test in {@code papyrus-api} to spin up a real Testcontainers
 * Postgres instance (existing tests here are unit/slice tests); the module already
 * declared the {@code testcontainers-postgresql}/{@code junit-jupiter} test dependencies
 * (unused until now), so no {@code pom.xml} change was needed.
 */
@Testcontainers
class EmbeddingDimensionGuardTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    private static DataSource dataSource;

    @BeforeAll
    static void migrateSchema() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        dataSource = new SingleConnectionDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    @AfterAll
    static void closeConnection() throws Exception {
        ((SingleConnectionDataSource) dataSource).close();
    }

    @Test
    void matchingDimensions_runsCleanlyWithoutThrowing() {
        // V1__create_schema.sql declares embedding vector(512); Voyage AI (the configured
        // provider) is also 512-dimensional.
        EmbeddingDimensionGuard guard = new EmbeddingDimensionGuard(fakeEmbeddingService(512), dataSource);

        guard.run(null);
        // No exception => success. Nothing further to assert; a clean run is the contract.
    }

    @Test
    void mismatchedDimensions_throwsWithClearMessage() {
        EmbeddingDimensionGuard guard = new EmbeddingDimensionGuard(fakeEmbeddingService(1536), dataSource);

        assertThatThrownBy(() -> guard.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Embedding dimension mismatch")
                .hasMessageContaining("expects dimension 1536")
                .hasMessageContaining("vector(512)")
                .hasMessageContaining("DATABASE_URL")
                .hasMessageContaining("papyrus.embedding.provider");
    }

    @Test
    void missingTable_doesNotThrow_logsAndSkips() throws Exception {
        // A brand-new, un-migrated database: document_chunks doesn't exist yet. The guard
        // must not crash here — Flyway (which runs earlier, during context refresh) is
        // responsible for creating the schema; the guard only ever runs after that in the
        // real boot path (see class javadoc). This proves the "table not found yet" path
        // degrades gracefully rather than blowing up if it were ever reached.
        try (PostgreSQLContainer<?> freshPostgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")) {
            freshPostgres.start();
            try (Connection conn = DriverManager.getConnection(
                    freshPostgres.getJdbcUrl(), freshPostgres.getUsername(), freshPostgres.getPassword());
                 Statement st = conn.createStatement()) {
                st.execute("CREATE EXTENSION IF NOT EXISTS vector");
            }
            DataSource freshDataSource = new SingleConnectionDataSource(
                    freshPostgres.getJdbcUrl(), freshPostgres.getUsername(), freshPostgres.getPassword());
            EmbeddingDimensionGuard guard = new EmbeddingDimensionGuard(fakeEmbeddingService(512), freshDataSource);

            guard.run(null);
            // No exception => success; the guard warned and skipped instead of crashing.
            ((SingleConnectionDataSource) freshDataSource).close();
        }
    }

    private static io.darqlab.papyrus.core.service.EmbeddingService fakeEmbeddingService(int dimensions) {
        return new io.darqlab.papyrus.core.service.EmbeddingService() {
            @Override
            public java.util.List<Float> embed(String text) {
                throw new UnsupportedOperationException("not used by this test");
            }

            @Override
            public int getDimensions() {
                return dimensions;
            }
        };
    }

    /**
     * Minimal {@link DataSource} wrapper around a single JDBC URL/user/password, so the
     * guard (which takes a plain {@code DataSource}) can be exercised without pulling in a
     * pooling datasource just for this test.
     */
    private static final class SingleConnectionDataSource implements DataSource {
        private final String url;
        private final String username;
        private final String password;

        SingleConnectionDataSource(String url, String username, String password) {
            this.url = url;
            this.username = username;
            this.password = password;
        }

        void close() {
            // no pooled resources to release
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
