package io.darqlab.papyrus.core.domain;

import java.time.Instant;
import java.util.UUID;

public record IngestionJob(
        UUID id,
        JobStatus status,
        Integer total,
        int processed,
        int failed,
        Instant createdAt,
        Instant updatedAt
) {
    public IngestionJob withStatus(JobStatus newStatus) {
        return new IngestionJob(id, newStatus, total, processed, failed, createdAt, Instant.now());
    }

    public IngestionJob incrementProcessed() {
        return new IngestionJob(id, status, total, processed + 1, failed, createdAt, Instant.now());
    }

    public IngestionJob incrementFailed() {
        return new IngestionJob(id, status, total, processed, failed + 1, createdAt, Instant.now());
    }
}
