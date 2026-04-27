package io.darqlab.papyrus.core.domain;

import java.time.Instant;
import java.util.UUID;

public record Source(
        UUID id,
        String filename,
        String archiveFilename,
        UUID archiveSourceId,
        String contentType,
        IngestionStatus status,
        Instant createdAt,
        String chunkingStrategy,
        Integer chunkingMaxTokens,
        Integer chunkingOverlapTokens
) {}
