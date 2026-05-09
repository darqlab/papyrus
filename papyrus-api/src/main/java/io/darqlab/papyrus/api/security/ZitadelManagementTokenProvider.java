package io.darqlab.papyrus.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Provides the Bearer token for Zitadel Management API calls.
 * Uses a Personal Access Token (PAT) configured via ZITADEL_MGMT_PAT —
 * no OAuth2 token exchange needed; the PAT is used directly.
 */
@Component
public class ZitadelManagementTokenProvider {

    private final String pat;

    public ZitadelManagementTokenProvider(
            @Value("${zitadel.mgmt.pat:}") String pat) {
        this.pat = pat;
    }

    public String getToken() {
        if (pat.isBlank()) {
            throw new IllegalStateException(
                    "ZITADEL_MGMT_PAT is not configured — set it to the papyrus-management service user PAT");
        }
        return pat;
    }
}
