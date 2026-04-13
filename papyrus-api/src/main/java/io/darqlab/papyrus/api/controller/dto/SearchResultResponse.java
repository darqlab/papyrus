package io.darqlab.papyrus.api.controller.dto;

import java.util.UUID;

public record SearchResultResponse(String content, double score,
                                   UUID sourceId, String sourceFilename, String archiveFilename, int chunkIndex) {}
