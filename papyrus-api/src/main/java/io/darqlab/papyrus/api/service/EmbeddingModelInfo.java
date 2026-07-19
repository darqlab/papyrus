package io.darqlab.papyrus.api.service;

/**
 * Result of {@link EmbeddingModelInfoService#describe(String)}.
 *
 * @param model      the embedding model name, e.g. {@code voyage-3-lite}
 * @param dimensions the vector dimension for that model, or {@code null} if unknown
 * @param database   the live database name this was resolved from (or against)
 * @param matched    {@code true} if {@code database} is a recognized
 *                   {@code papyrus_<model>} name from the ADR-009 naming contract;
 *                   {@code false} if this is a best-effort fallback (e.g. the
 *                   pre-reingest {@code papyrus} baseline, or an unrecognized name)
 */
public record EmbeddingModelInfo(String model, Integer dimensions, String database, boolean matched) {
}
