package io.darqlab.papyrus.api.controller.dto;

import java.util.List;
import java.util.UUID;

public record BatchUploadResponse(UUID jobId, int total, int processed, int failed,
                                   String status, List<UploadResponse> results) {}
