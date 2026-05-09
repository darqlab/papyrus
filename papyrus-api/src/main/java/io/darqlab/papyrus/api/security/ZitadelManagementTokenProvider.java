package io.darqlab.papyrus.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class ZitadelManagementTokenProvider {

    private final RestClient restClient = RestClient.create();
    private final String issuerUri;
    private final String clientId;
    private final String clientSecret;

    private record CachedToken(String token, Instant expiresAt) {}
    private final AtomicReference<CachedToken> cache = new AtomicReference<>();

    public ZitadelManagementTokenProvider(
            @Value("${zitadel.issuer-uri:}") String issuerUri,
            @Value("${zitadel.mgmt.client-id:}") String clientId,
            @Value("${zitadel.mgmt.client-secret:}") String clientSecret) {
        this.issuerUri    = issuerUri;
        this.clientId     = clientId;
        this.clientSecret = clientSecret;
    }

    public String getToken() {
        CachedToken cached = cache.get();
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.token();
        }
        return fetchAndCache();
    }

    @SuppressWarnings("unchecked")
    private synchronized String fetchAndCache() {
        // double-checked inside synchronized
        CachedToken cached = cache.get();
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.token();
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("scope", "openid");

        Map<String, Object> response = restClient.post()
                .uri(issuerUri + "/oauth/v2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(h -> h.setBasicAuth(clientId, clientSecret))
                .body(form)
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("access_token")) {
            throw new IllegalStateException("Failed to obtain Zitadel management access token");
        }

        String token = (String) response.get("access_token");
        int expiresIn = response.get("expires_in") instanceof Number n ? n.intValue() : 3600;
        Instant expiresAt = Instant.now().plusSeconds(expiresIn - 60);

        cache.set(new CachedToken(token, expiresAt));
        return token;
    }
}
