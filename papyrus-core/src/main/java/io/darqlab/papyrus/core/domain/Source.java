package io.darqlab.papyrus.core.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Summary view of a Document — used for list endpoints.
 * Does not include extracted text or full metadata.
 */
public record Source(
        UUID id,
        String filename,
        String contentType,
        IngestionStatus status,
        Instant createdAt
) {}
