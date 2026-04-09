package io.darqlab.papyrus.api.controller.dto;

import io.darqlab.papyrus.core.domain.JobStatus;

import java.util.UUID;

public record JobResponse(UUID id, JobStatus status, int total, int processed, int failed) {}
