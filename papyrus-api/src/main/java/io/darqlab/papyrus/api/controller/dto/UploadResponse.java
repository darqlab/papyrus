package io.darqlab.papyrus.api.controller.dto;

import java.util.UUID;

public record UploadResponse(UUID sourceId, String filename, int chunkCount, String status, boolean duplicate) {}
