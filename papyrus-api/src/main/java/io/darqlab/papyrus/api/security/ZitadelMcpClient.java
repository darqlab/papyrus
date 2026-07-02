package io.darqlab.papyrus.api.security;

import java.time.Instant;

public record ZitadelMcpClient(
        String userId,
        String name,
        String role,
        String grantId,
        Instant createdAt
) {}
