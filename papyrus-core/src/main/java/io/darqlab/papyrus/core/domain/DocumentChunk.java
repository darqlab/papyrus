package io.darqlab.papyrus.core.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DocumentChunk(
        UUID id,
        UUID sourceId,
        int chunkIndex,
        Integer pageNumber,
        String content,
        Integer tokenCount,
        Map<String, Object> metadata,
        Instant createdAt
) {}
