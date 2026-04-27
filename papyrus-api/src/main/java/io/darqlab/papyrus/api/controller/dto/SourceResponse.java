package io.darqlab.papyrus.api.controller.dto;

import io.darqlab.papyrus.core.domain.IngestionStatus;

import java.time.Instant;
import java.util.UUID;

public record SourceResponse(UUID id, String filename, String archiveFilename, UUID archiveSourceId,
                              String contentType, IngestionStatus status, Instant createdAt,
                              String chunkingStrategy, Integer chunkingMaxTokens, Integer chunkingOverlapTokens) {}
