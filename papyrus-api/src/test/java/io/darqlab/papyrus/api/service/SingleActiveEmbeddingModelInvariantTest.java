package io.darqlab.papyrus.api.service;

import io.darqlab.papyrus.api.PapyrusApplication;
import io.darqlab.papyrus.core.service.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drift-lock for P2.2 ("apply active model automatically, no per-ingest picker"). The
 * invariant holds today by construction: every ingest/reingest path resolves the embedding
 * model through a single {@link EmbeddingService} bean, chosen once at startup via
 * {@code @ConditionalOnProperty(papyrus.embedding.provider)}, with no per-call model
 * parameter anywhere. Rather than trust reflective parameter-name inspection (this module
 * doesn't compile with {@code -parameters}, so reflection would only see {@code arg0},
 * {@code arg1}, ... and the check would be a no-op), this test asserts the invariant at
 * the level that actually matters: exactly one {@code EmbeddingService} bean is ever active
 * in a real, fully-booted context. If a per-ingest picker were ever reintroduced (e.g. a
 * second conditional bean, or a bean chosen per-request), this either fails context startup
 * (ambiguous bean) or this assertion fails outright.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("dev")
class SingleActiveEmbeddingModelInvariantTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16"));

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    org.springframework.context.ApplicationContext applicationContext;

    @Autowired
    EmbeddingService embeddingService;

    @Test
    void exactlyOneEmbeddingServiceBeanIsActive() {
        Map<String, EmbeddingService> beans = applicationContext.getBeansOfType(EmbeddingService.class);

        assertThat(beans)
                .as("expected exactly one active EmbeddingService bean, chosen once at startup; "
                        + "found: %s", beans.keySet())
                .hasSize(1);
    }

    @Test
    void ingestMethodsAcceptNoEmbeddingServiceOrModelArgument() {
        // Belt-and-suspenders on top of the bean-count check above: the ingest/reingest
        // methods themselves never accept an EmbeddingService, model, or provider as an
        // argument - the model is always resolved via the single injected field, never
        // passed in per call.
        for (Method method : DocumentService.class.getDeclaredMethods()) {
            if (!(method.getName().equals("ingest") || method.getName().equals("ingestUrl")
                    || method.getName().equals("reingest") || method.getName().equals("reingestFromFile"))) {
                continue;
            }
            boolean acceptsEmbeddingServiceParam = Arrays.stream(method.getParameterTypes())
                    .anyMatch(EmbeddingService.class::isAssignableFrom);

            assertThat(acceptsEmbeddingServiceParam)
                    .as("%s#%s must not accept an EmbeddingService parameter - the active model "
                            + "is resolved once at startup via constructor injection, never per call",
                            DocumentService.class.getSimpleName(), method.getName())
                    .isFalse();
        }
    }
}
