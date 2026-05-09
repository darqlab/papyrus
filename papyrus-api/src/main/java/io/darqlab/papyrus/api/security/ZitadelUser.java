package io.darqlab.papyrus.api.security;

import java.time.Instant;

public record ZitadelUser(
        String userId,
        String displayName,
        String email,
        String role,
        String grantId,
        Instant createdAt,
        Instant lastLogin
) {}
