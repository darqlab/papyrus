package io.darqlab.papyrus.pipeline.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "papyrus")
public record PapyrusProperties(
        EmbeddingProperties embedding,
        ChunkingProperties chunking,
        SearchProperties search,
        OcrProperties ocr
) {
    public record EmbeddingProperties(
            String provider,
            VoyageProperties voyage,
            OllamaProperties ollama
    ) {}

    public record VoyageProperties(String apiKey, String model) {}

    public record OllamaProperties(String baseUrl, String model) {}

    public record ChunkingProperties(
            ChunkingStrategy strategy,
            int maxTokens,
            int overlapTokens
    ) {}

    public record SearchProperties(int defaultTopK) {}

    public record OcrProperties(CorrectionProperties correction) {}

    public record CorrectionProperties(boolean enabled, String apiKey, String model) {}
}
