package io.darqlab.papyrus.ingestor;

import io.darqlab.papyrus.ingestor.model.TargetModelSpec;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves a {@code TARGET_MODEL} value (e.g. {@code voyage-3-lite}) to its
 * {@code (provider, dim, databaseName)} triple per the naming contract shared with the
 * ops runbook (ADR-009 §Naming convention / reingest-model-switch-ip.md §Model → Database
 * Naming). This is the single source of truth for the mapping in this module — do not
 * improvise database names elsewhere.
 *
 * <p>Only {@code voyage-3-lite} is currently ingestable: it is the only model with a
 * working {@code EmbeddingService} bean ({@code VoyageAiEmbeddingService} in
 * papyrus-pipeline, hardcoded {@code DIMENSIONS = 512}). The other two rows exist so that
 * adding a provider later is purely additive (implement the service + this table already
 * has the row) — {@link #resolve(String)} fails clearly for those until then.
 */
@Component
public class TargetModelResolver {

    private static final Map<String, TargetModelSpec> REGISTRY = buildRegistry();

    private static Map<String, TargetModelSpec> buildRegistry() {
        Map<String, TargetModelSpec> registry = new LinkedHashMap<>();
        registry.put("voyage-3-lite",
                new TargetModelSpec("voyage-3-lite", "voyage", 512, "papyrus_voyage3lite", true));
        // Not yet ingestable — no EmbeddingService implementation exists for these providers.
        registry.put("text-embedding-3-small",
                new TargetModelSpec("text-embedding-3-small", "openai", 1536, "papyrus_openai3small", false));
        registry.put("nomic-embed-text",
                new TargetModelSpec("nomic-embed-text", "ollama", 768, "papyrus_nomic", false));
        return Map.copyOf(registry);
    }

    /**
     * Resolves the given target model name.
     *
     * @throws IllegalArgumentException if the model is not in the naming contract at all
     * @throws IllegalStateException    if the model is in the contract but has no
     *                                  {@code EmbeddingService} implementation yet
     */
    public TargetModelSpec resolve(String targetModel) {
        if (targetModel == null || targetModel.isBlank()) {
            throw new IllegalArgumentException(
                    "TARGET_MODEL is not set. Known models: " + REGISTRY.keySet());
        }
        TargetModelSpec spec = REGISTRY.get(targetModel);
        if (spec == null) {
            throw new IllegalArgumentException(
                    "Unknown TARGET_MODEL '" + targetModel + "'. Known models: " + REGISTRY.keySet()
                            + ". New models are added by extending the naming contract in ADR-009 "
                            + "and this resolver — never by ad-hoc naming.");
        }
        if (!spec.embeddingServiceAvailable()) {
            throw new IllegalStateException(
                    "TARGET_MODEL '" + targetModel + "' is in the naming contract but has no "
                            + "EmbeddingService implementation yet (provider=" + spec.provider() + "). "
                            + "Only 'voyage-3-lite' is currently ingestable. Implement an EmbeddingService "
                            + "for this provider and flip embeddingServiceAvailable=true in "
                            + TargetModelResolver.class.getSimpleName() + " before targeting it.");
        }
        return spec;
    }
}
