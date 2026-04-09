package io.darqlab.papyrus.core.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record Document(
        UUID id,
        String filename,
        String contentType,
        Long fileSize,
        Integer pageCount,
        String language,
        IngestionStatus status,
        String error,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
    public Document withStatus(IngestionStatus newStatus) {
        return new Document(id, filename, contentType, fileSize, pageCount,
                language, newStatus, error, metadata, createdAt, Instant.now());
    }

    public Document withError(String newError) {
        return new Document(id, filename, contentType, fileSize, pageCount,
                language, IngestionStatus.FAILED, newError, metadata, createdAt, Instant.now());
    }
}
