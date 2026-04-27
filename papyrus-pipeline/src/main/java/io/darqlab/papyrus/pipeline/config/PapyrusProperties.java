package io.darqlab.papyrus.pipeline.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "papyrus")
public record PapyrusProperties(
        EmbeddingProperties embedding,
        ChunkingProperties chunking,
        SearchProperties search,
        OcrProperties ocr,
        ArchiveProperties archive,
        ChatProperties chat
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
            int overlapTokens,
            SemanticChunkingProperties semantic,
            SectionChunkingProperties section
    ) {}

    public record SemanticChunkingProperties(float threshold, int minSentences) {}

    public record SectionChunkingProperties(String pattern, int minTokens) {}

    public record SearchProperties(int defaultTopK) {}

    public record OcrProperties(CorrectionProperties correction) {}

    public record CorrectionProperties(
            boolean enabled,
            String provider,
            String model,
            AnthropicCorrectionProperties anthropic,
            OllamaCorrectionProperties ollama,
            EvolinkCorrectionProperties evolink
    ) {}

    public record AnthropicCorrectionProperties(String apiKey) {}
    public record OllamaCorrectionProperties(String baseUrl) {}
    public record EvolinkCorrectionProperties(String apiKey) {}

    public record ArchiveProperties(boolean enabled, String path, Map<String, String> abbreviations) {}

    public record ChatProperties(
            String provider,
            AnthropicChatProperties anthropic,
            OllamaChatProperties ollama,
            EvolinkChatProperties evolink
    ) {}

    public record AnthropicChatProperties(String apiKey, String model) {}

    public record OllamaChatProperties(String baseUrl, String model) {}

    public record EvolinkChatProperties(String apiKey, String baseUrl, String model) {}
}
