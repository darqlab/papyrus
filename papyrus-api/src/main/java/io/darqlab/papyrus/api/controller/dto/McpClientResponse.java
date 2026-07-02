package io.darqlab.papyrus.api.controller.dto;

import io.darqlab.papyrus.api.security.ZitadelMcpClient;

import java.time.Instant;

public record McpClientResponse(
        String userId,
        String name,
        String role,
        String grantId,
        Instant createdAt
) {
    public static McpClientResponse from(ZitadelMcpClient c) {
        return new McpClientResponse(c.userId(), c.name(), c.role(), c.grantId(), c.createdAt());
    }
}
