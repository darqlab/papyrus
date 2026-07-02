package io.darqlab.papyrus.api.security;

/** The raw PAT string is only ever available at creation time — Zitadel never returns it again. */
public record ZitadelMcpToken(
        String userId,
        String tokenId,
        String token
) {}
