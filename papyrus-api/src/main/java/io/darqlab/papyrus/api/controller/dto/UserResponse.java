package io.darqlab.papyrus.api.controller.dto;

import io.darqlab.papyrus.api.security.ZitadelUser;

import java.time.Instant;

public record UserResponse(
        String userId,
        String displayName,
        String email,
        String role,
        String grantId,
        Instant createdAt,
        Instant lastLogin
) {
    public static UserResponse from(ZitadelUser u) {
        return new UserResponse(u.userId(), u.displayName(), u.email(),
                u.role(), u.grantId(), u.createdAt(), u.lastLogin());
    }
}
