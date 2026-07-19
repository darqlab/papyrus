package io.darqlab.papyrus.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reverse-lookup for the ADR-009 "Model → Database Naming" contract
 * ({@code development/decisions/ADR-009-on-demand-ingestion-blue-green.md} §Naming
 * convention): given the *live* database name (whatever {@code DATABASE_URL} currently
 * points at), resolve which embedding model/dimension it corresponds to.
 *
 * <p>This mirrors — deliberately, not by shared code — the forward-direction table in
 * {@code papyrus-ingestor}'s {@code TargetModelResolver}. {@code papyrus-api} does not
 * depend on {@code papyrus-ingestor}, so the three-row naming table is kept here as a
 * small local copy rather than introducing a cross-module dependency for one lookup.
 * If the naming contract ever grows a fourth model, update both tables.
 *
 * <p>Task scope: this is the read-only <em>display</em> half of reingest task P2.1. It does
 * not apply the model to ingest actions (P2.2) and does not guard against
 * config/DB-name mismatch at startup (P2.3) — those are separate, later tasks.
 */
@Service
public class EmbeddingModelInfoService {

    /** One row of the naming contract, keyed by database name for the reverse lookup. */
    private record ModelSpec(String modelName, int dimensions) {
    }

    private static final Map<String, ModelSpec> BY_DATABASE = buildRegistry();

    private static Map<String, ModelSpec> buildRegistry() {
        Map<String, ModelSpec> registry = new LinkedHashMap<>();
        registry.put("papyrus_voyage3lite", new ModelSpec("voyage-3-lite", 512));
        registry.put("papyrus_openai3small", new ModelSpec("text-embedding-3-small", 1536));
        registry.put("papyrus_nomic", new ModelSpec("nomic-embed-text", 768));
        return Map.copyOf(registry);
    }

    private final String configuredModel;

    public EmbeddingModelInfoService(
            @Value("${papyrus.embedding.voyage.model:voyage-3-lite}") String configuredModel) {
        this.configuredModel = configuredModel;
    }

    /**
     * Describes the embedding model behind the given live database name.
     *
     * @param databaseName the catalog name of the currently connected datasource, e.g.
     *                      {@code papyrus_voyage3lite}, or the pre-reingest default
     *                      {@code papyrus}
     * @return the resolved (or best-effort fallback) model info; never throws — a
     * database name that is not in the naming contract yet (e.g. the plain
     * {@code papyrus} pre-reingest baseline) falls back to the configured model rather
     * than failing the request. The mismatch guard (P2.3) is out of scope here.
     */
    public EmbeddingModelInfo describe(String databaseName) {
        ModelSpec spec = BY_DATABASE.get(databaseName);
        if (spec != null) {
            return new EmbeddingModelInfo(spec.modelName(), spec.dimensions(), databaseName, true);
        }

        // Fallback: database name isn't (yet) a papyrus_<model>-named DB — most notably
        // the pre-reingest baseline "papyrus". Best-effort label it using the configured
        // model rather than 404/500ing the /manage page.
        Integer fallbackDimensions = dimensionsForModel(configuredModel);
        return new EmbeddingModelInfo(configuredModel, fallbackDimensions, databaseName, false);
    }

    private static Integer dimensionsForModel(String modelName) {
        return BY_DATABASE.values().stream()
                .filter(spec -> spec.modelName().equals(modelName))
                .map(ModelSpec::dimensions)
                .findFirst()
                .orElse(null);
    }
}
