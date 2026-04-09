package io.darqlab.papyrus.api.controller.dto;

import java.util.UUID;

public record SearchRequest(String query, Integer topK, UUID sourceId) {}
