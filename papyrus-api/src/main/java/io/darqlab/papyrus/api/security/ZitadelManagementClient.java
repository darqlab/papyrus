package io.darqlab.papyrus.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class ZitadelManagementClient {

    private final RestClient restClient = RestClient.create();
    private final ZitadelManagementTokenProvider tokenProvider;
    private final String issuerUri;
    private final String projectId;

    public ZitadelManagementClient(
            ZitadelManagementTokenProvider tokenProvider,
            @Value("${zitadel.issuer-uri:}") String issuerUri,
            @Value("${zitadel.project-id:}") String projectId) {
        this.tokenProvider = tokenProvider;
        this.issuerUri     = issuerUri;
        this.projectId     = projectId;
    }

    // ── List ─────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public List<ZitadelUser> listProjectUsers() {
        Map<String, Object> body = Map.of(
                "projectId", projectId,
                "queries",   List.of()
        );

        Map<String, Object> response = post("/management/v1/usergrants/_search", body);
        List<Map<String, Object>> result = (List<Map<String, Object>>) response.getOrDefault("result", List.of());

        return result.stream()
                .map(this::toZitadelUser)
                .toList();
    }

    // ── Invite ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public void inviteUser(String firstName, String lastName, String email, String role) {
        // Step 1: create human user — Zitadel sends initialization email automatically
        Map<String, Object> createBody = Map.of(
                "profile", Map.of(
                        "firstName",   firstName,
                        "lastName",    lastName,
                        "displayName", firstName + " " + lastName
                ),
                "email", Map.of(
                        "email",             email,
                        "isEmailVerified",   false,
                        "sendCode",          Map.of()
                )
        );
        Map<String, Object> createResponse = post("/management/v1/users/human", createBody);
        String userId = (String) createResponse.get("userId");
        if (userId == null) {
            throw new IllegalStateException("Zitadel did not return a userId after user creation");
        }

        // Step 2: grant project role
        Map<String, Object> grantBody = Map.of(
                "userId",   userId,
                "roleKeys", List.of(role)
        );
        post("/management/v1/usergrants", grantBody);
    }

    // ── Change role ───────────────────────────────────────────────────────────

    public void changeRole(String grantId, String newRole) {
        Map<String, Object> body = Map.of("roleKeys", List.of(newRole));
        put("/management/v1/usergrants/" + grantId, body);
    }

    // ── Remove (revoke grant only) ────────────────────────────────────────────

    public void removeUser(String grantId) {
        delete("/management/v1/usergrants/" + grantId);
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Object body) {
        Map<String, Object> response = restClient.post()
                .uri(issuerUri + path)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBearerAuth(tokenProvider.getToken()))
                .body(body)
                .retrieve()
                .body(Map.class);
        return response != null ? response : Map.of();
    }

    private void put(String path, Object body) {
        restClient.put()
                .uri(issuerUri + path)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBearerAuth(tokenProvider.getToken()))
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private void delete(String path) {
        restClient.delete()
                .uri(issuerUri + path)
                .headers(h -> h.setBearerAuth(tokenProvider.getToken()))
                .retrieve()
                .toBodilessEntity();
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private ZitadelUser toZitadelUser(Map<String, Object> grant) {
        String grantId     = (String) grant.get("id");
        String userId      = (String) grant.get("userId");
        String displayName = (String) grant.getOrDefault("displayName", "");
        String email       = (String) grant.getOrDefault("userEmail", "");

        List<String> roles = (List<String>) grant.getOrDefault("roles", List.of());
        String role = roles.isEmpty() ? "READER" : roles.get(0).toUpperCase();

        // createdAt from grant details
        Instant createdAt = null;
        Map<String, Object> details = (Map<String, Object>) grant.get("details");
        if (details != null && details.get("creationDate") instanceof String s) {
            try { createdAt = Instant.parse(s); } catch (Exception ignored) {}
        }

        // lastLogin requires a separate user fetch — read from annotation field if present
        Instant lastLogin = null;
        if (grant.get("lastLogin") instanceof String s) {
            try { lastLogin = Instant.parse(s); } catch (Exception ignored) {}
        }

        return new ZitadelUser(userId, displayName, email, role, grantId, createdAt, lastLogin);
    }
}
