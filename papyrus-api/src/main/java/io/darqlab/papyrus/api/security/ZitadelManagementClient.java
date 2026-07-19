package io.darqlab.papyrus.api.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ZitadelManagementClient {

    private static final Logger log = LoggerFactory.getLogger(ZitadelManagementClient.class);

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
                "queries", List.of(
                        Map.of("projectIdQuery", Map.of("projectId", projectId))
                )
        );

        Map<String, Object> response = post("/management/v1/users/grants/_search", body);
        List<Map<String, Object>> result = (List<Map<String, Object>>) response.getOrDefault("result", List.of());

        Map<String, Instant> lastLogins = fetchLastLogins();

        return result.stream()
                .filter(g -> !"TYPE_MACHINE".equals(g.get("userType")))
                .map(g -> toZitadelUser(g, lastLogins))
                .toList();
    }

    /** Reconstructs last-login timestamps from the instance Admin Events API, since neither the
     *  v1 grants search nor the v2 user search expose a lastLogin field. Requires the service
     *  account to hold the instance-level IAM_OWNER_VIEWER role (or higher) — falls back to an
     *  empty map (no last-login data, not a broken user list) if that role is missing or the
     *  call otherwise fails. */
    @SuppressWarnings("unchecked")
    private Map<String, Instant> fetchLastLogins() {
        Map<String, Object> body = Map.of(
                "asc", false,
                "eventTypes", List.of("user.human.password.check.succeeded"),
                "aggregateTypes", List.of("user"),
                "limit", 500
        );

        Map<String, Object> response;
        try {
            response = post("/admin/v1/events/_search", body);
        } catch (Exception e) {
            log.warn("Failed to fetch last-login events from Zitadel; last-login column will be empty", e);
            return Map.of();
        }

        List<Map<String, Object>> events = (List<Map<String, Object>>) response.getOrDefault("events", List.of());
        Map<String, Instant> lastLogins = new HashMap<>();
        for (Map<String, Object> event : events) {
            Map<String, Object> aggregate = (Map<String, Object>) event.get("aggregate");
            if (aggregate == null) continue;
            String userId = (String) aggregate.get("id");
            if (userId == null || lastLogins.containsKey(userId)) continue; // events are newest-first: keep the first hit per user

            Object creationDate = event.get("creationDate");
            if (creationDate instanceof String s) {
                try { lastLogins.put(userId, Instant.parse(s)); } catch (Exception ignored) {}
            }
        }
        return lastLogins;
    }

    // ── Invite ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public void inviteUser(String firstName, String lastName, String email, String role) {
        // Step 1: create human user — Zitadel sends initialization email automatically
        String userName = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        Map<String, Object> createBody = Map.of(
                "userName", userName,
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

        // Step 2: grant project role — path: /users/{userId}/grants
        Map<String, Object> grantBody = Map.of(
                "projectId", projectId,
                "roleKeys",  List.of(role)
        );
        post("/management/v1/users/" + userId + "/grants", grantBody);
    }

    // ── Change role ───────────────────────────────────────────────────────────

    public void changeRole(String userId, String grantId, String newRole) {
        Map<String, Object> body = Map.of("roleKeys", List.of(newRole));
        put("/management/v1/users/" + userId + "/grants/" + grantId, body);
    }

    // ── Remove (revoke grant only) ────────────────────────────────────────────

    public void removeUser(String userId, String grantId) {
        delete("/management/v1/users/" + userId + "/grants/" + grantId);
    }

    // ── MCP clients (machine users + PATs) ──────────────────────────────────────

    @SuppressWarnings("unchecked")
    public List<ZitadelMcpClient> listMcpClients() {
        Map<String, Object> body = Map.of(
                "queries", List.of(
                        Map.of("projectIdQuery", Map.of("projectId", projectId))
                )
        );

        Map<String, Object> response = post("/management/v1/users/grants/_search", body);
        List<Map<String, Object>> result = (List<Map<String, Object>>) response.getOrDefault("result", List.of());

        return result.stream()
                .filter(g -> "TYPE_MACHINE".equals(g.get("userType")))
                .map(this::toZitadelMcpClient)
                .toList();
    }

    /** Creates a machine user, grants it a project role, and issues a Personal Access Token.
     *  The returned token is shown only once — Zitadel does not store or return it again. */
    public ZitadelMcpToken createMcpClient(String name, String role) {
        String userName = "mcp-" + name.toLowerCase().replaceAll("[^a-z0-9-]+", "-");
        Map<String, Object> createBody = Map.of(
                "userName",        userName,
                "name",            name,
                "description",     "Papyrus MCP client token",
                "accessTokenType", "ACCESS_TOKEN_TYPE_BEARER"
        );
        Map<String, Object> createResponse = post("/management/v1/users/machine", createBody);
        String userId = (String) createResponse.get("userId");
        if (userId == null) {
            throw new IllegalStateException("Zitadel did not return a userId after machine user creation");
        }

        Map<String, Object> grantBody = Map.of(
                "projectId", projectId,
                "roleKeys",  List.of(role)
        );
        post("/management/v1/users/" + userId + "/grants", grantBody);

        Map<String, Object> patResponse = post("/management/v1/users/" + userId + "/pats", Map.of());
        String tokenId = (String) patResponse.get("tokenId");
        String token   = (String) patResponse.get("token");
        if (token == null) {
            throw new IllegalStateException("Zitadel did not return a token after PAT creation");
        }

        return new ZitadelMcpToken(userId, tokenId, token);
    }

    /** Deletes the machine user outright — cascades its PAT and project grant.
     *  Unlike human {@link #removeUser}, a bare grant revocation is not enough here:
     *  the introspector falls back to ROLE_READER when a token has no role claim,
     *  so the PAT/user itself must be removed to fully cut off access. */
    public void deleteMcpClient(String userId) {
        delete("/management/v1/users/" + userId);
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
    private ZitadelUser toZitadelUser(Map<String, Object> grant, Map<String, Instant> lastLogins) {
        String grantId     = (String) grant.get("id");
        String userId      = (String) grant.get("userId");
        String displayName = (String) grant.getOrDefault("displayName", "");
        String email       = (String) grant.getOrDefault("email", "");

        List<String> roleKeys = (List<String>) grant.getOrDefault("roleKeys", List.of());
        String role = roleKeys.isEmpty() ? "READER" : roleKeys.get(0).toUpperCase();

        Instant createdAt = null;
        Map<String, Object> details = (Map<String, Object>) grant.get("details");
        if (details != null && details.get("creationDate") instanceof String s) {
            try { createdAt = Instant.parse(s); } catch (Exception ignored) {}
        }

        Instant lastLogin = lastLogins.get(userId);

        return new ZitadelUser(userId, displayName, email, role, grantId, createdAt, lastLogin);
    }

    @SuppressWarnings("unchecked")
    private ZitadelMcpClient toZitadelMcpClient(Map<String, Object> grant) {
        String grantId = (String) grant.get("id");
        String userId  = (String) grant.get("userId");
        String name    = (String) grant.getOrDefault("displayName", "");

        List<String> roleKeys = (List<String>) grant.getOrDefault("roleKeys", List.of());
        String role = roleKeys.isEmpty() ? "READER" : roleKeys.get(0).toUpperCase();

        Instant createdAt = null;
        Map<String, Object> details = (Map<String, Object>) grant.get("details");
        if (details != null && details.get("creationDate") instanceof String s) {
            try { createdAt = Instant.parse(s); } catch (Exception ignored) {}
        }

        return new ZitadelMcpClient(userId, name, role, grantId, createdAt);
    }
}
