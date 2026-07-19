package io.darqlab.papyrus.api.service;

import io.darqlab.papyrus.api.PapyrusApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the real {@link PapyrusApplication} context (the "dev" profile disables Zitadel
 * security so the whole context can start without external OIDC config — see
 * {@code DevSecurityConfig}) against a fresh, un-migrated Testcontainers Postgres+pgvector
 * instance.
 *
 * <p>This is the empirical check the P2.3 brief asked for: rather than assuming Spring
 * Boot's documented guarantee that {@code FlywayMigrationInitializer} (an
 * {@code InitializingBean}, run during {@code ApplicationContext} refresh) always
 * completes before {@code ApplicationRunner} beans (run by {@code SpringApplication.run()}
 * strictly after refresh returns), this test proves it against this actual application:
 * if the ordering did not hold, {@link EmbeddingDimensionGuard} would either throw
 * (mismatch/missing-table false positive) or the context would fail to start, since the
 * schema does not exist until Flyway creates it. A successful, fully-started context here
 * means the guard ran — and found a migrated schema — after Flyway, not before.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("dev")
class EmbeddingDimensionGuardOrderingTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16"));

    @org.springframework.test.context.DynamicPropertySource
    static void datasourceProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Voyage AI (512 dims) is the default provider; V1__create_schema.sql declares
        // embedding vector(512), so this is the "matching dimensions" case — the point of
        // this test is Flyway-before-ApplicationRunner ordering, not the guard's own logic
        // (already covered by EmbeddingDimensionGuardTest).
    }

    @org.springframework.beans.factory.annotation.Autowired
    javax.sql.DataSource dataSource;

    @Test
    void contextStartsCleanly_provingFlywayRanBeforeTheGuard() throws SQLException {
        // If we get here at all, the context (including the EmbeddingDimensionGuard
        // ApplicationRunner) finished starting without throwing. Additionally assert the
        // schema really was migrated by the time of this test, confirming Flyway indeed
        // ran as part of that same successful startup.
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            var rs = st.executeQuery(
                    "SELECT atttypmod FROM pg_attribute "
                            + "WHERE attrelid = 'document_chunks'::regclass AND attname = 'embedding'");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(512);
        }
    }
}
