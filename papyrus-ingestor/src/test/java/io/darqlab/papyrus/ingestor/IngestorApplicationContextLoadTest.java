package io.darqlab.papyrus.ingestor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Boots the real {@link IngestorApplication} context with no web server. This is the check
 * that would have caught the P1.3 regression where {@code ReingestOrchestrator} declared two
 * constructors (the public one used by Spring, plus a package-visible one for tests) with
 * neither annotated {@code @Autowired} — Spring's constructor resolution sees both declared
 * constructors regardless of visibility, and without a single unambiguous candidate, context
 * refresh fails. That bug was only caught live (Castellan's ephemeral run boot-failed on the
 * production host); a plain context-load test would have failed CI instead.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "ingestor.target-model=voyage-3-lite",
        "ingestor.db.host=localhost",
        "ingestor.db.port=5432",
        "ingestor.db.admin-database=postgres",
        "ingestor.db.admin-user=test",
        "ingestor.db.admin-password=test",
        "ingestor.db.source-database=papyrus",
        "ingestor.archive-path=/tmp",
        "ingestor.voyage.api-key=test",
        "ingestor.voyage.model=voyage-3-lite"
})
class IngestorApplicationContextLoadTest {

    @Test
    void contextLoads() {
        // No assertions needed: a failed constructor-injection wiring (e.g. an ambiguous
        // multi-constructor bean) throws during context refresh, before this method runs.
    }
}
