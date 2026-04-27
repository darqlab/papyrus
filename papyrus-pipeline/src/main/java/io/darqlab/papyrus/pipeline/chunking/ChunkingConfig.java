package io.darqlab.papyrus.pipeline.chunking;

import io.darqlab.papyrus.pipeline.config.ChunkingStrategy;

public record ChunkingConfig(
        ChunkingStrategy strategy,
        int maxTokens,
        int overlapTokens
) {}
