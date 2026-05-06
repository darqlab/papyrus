package io.darqlab.papyrus.api.controller.dto;

import java.util.UUID;

public record AsyncUploadResponse(UUID jobId, String filename, String status, boolean duplicate) {}
