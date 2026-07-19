package io.darqlab.papyrus.ingestor.model;

/**
 * One row of the ADR-009 "Model → Database Naming" contract
 * (see {@code development/decisions/ADR-009-on-demand-ingestion-blue-green.md} and
 * {@code development/reingest/reingest-model-switch-ip.md}).
 *
 * <p>{@code embeddingServiceAvailable} tracks whether an {@code EmbeddingService} bean
 * actually exists in this repo for the model's provider. As of this task, only
 * {@code voyage-3-lite} (provider {@code voyage}) has a working implementation
 * ({@code VoyageAiEmbeddingService} in papyrus-pipeline). The OpenAI and Ollama rows are
 * defined here as data so that adding a provider later is just: implement
 * {@code EmbeddingService} for it, then flip this flag to {@code true} — no changes to
 * ingestor control flow.
 *
 * @param modelName                  the embedding model identifier, e.g. {@code voyage-3-lite}
 * @param provider                   the embedding provider, e.g. {@code voyage}, {@code openai}, {@code ollama}
 * @param dimensions                 the vector dimension this model produces
 * @param databaseName               the {@code papyrus_<normalized-model>} database name (naming contract)
 * @param embeddingServiceAvailable  whether an {@code EmbeddingService} implementation exists for this provider
 */
public record TargetModelSpec(
        String modelName,
        String provider,
        int dimensions,
        String databaseName,
        boolean embeddingServiceAvailable
) {
}
