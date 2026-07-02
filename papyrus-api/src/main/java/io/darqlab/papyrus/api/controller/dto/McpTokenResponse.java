package io.darqlab.papyrus.api.controller.dto;

import io.darqlab.papyrus.api.security.ZitadelMcpToken;

public record McpTokenResponse(
        String userId,
        String tokenId,
        String token
) {
    public static McpTokenResponse from(ZitadelMcpToken t) {
        return new McpTokenResponse(t.userId(), t.tokenId(), t.token());
    }
}
