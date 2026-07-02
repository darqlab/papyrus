package io.darqlab.papyrus.api.controller.dto;

public record CreateMcpClientRequest(
        String name,
        String role
) {}
