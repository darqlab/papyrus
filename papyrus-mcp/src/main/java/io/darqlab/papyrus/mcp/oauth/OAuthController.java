package io.darqlab.papyrus.mcp.oauth;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal OAuth 2.0 Authorization Server for the Papyrus MCP endpoint.
 *
 * claude.ai requires OAuth before connecting to any remote MCP server. This
 * implementation auto-approves every authorization request and issues dummy
 * access tokens with no actual authentication — the server accepts any bearer
 * token (or no token) on the /mcp endpoint.
 *
 * Implements:
 *   POST /oauth/register    — Dynamic Client Registration (RFC 7591)
 *   GET  /oauth/authorize   — Authorization endpoint (RFC 6749) — auto-redirects
 *   POST /oauth/token       — Token endpoint (RFC 6749)
 */
@RestController
@RequestMapping("/oauth")
public class OAuthController {

    private final Map<String, List<String>> clients   = new ConcurrentHashMap<>();
    private final Map<String, String>       authCodes  = new ConcurrentHashMap<>();

    /** Dynamic Client Registration — RFC 7591 */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> body) {
        String clientId = "papyrus-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        @SuppressWarnings("unchecked")
        List<String> redirectUris = body.containsKey("redirect_uris")
                ? (List<String>) body.get("redirect_uris")
                : List.of();

        clients.put(clientId, redirectUris);

        return ResponseEntity.status(201).body(Map.of(
                "client_id",      clientId,
                "redirect_uris",  redirectUris,
                "grant_types",    List.of("authorization_code"),
                "response_types", List.of("code"),
                "token_endpoint_auth_method", "none"
        ));
    }

    /**
     * Authorization endpoint — auto-approves, immediately redirects to the
     * client's redirect_uri with an authorization code. No login UI is shown.
     */
    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(
            @RequestParam("response_type")            String responseType,
            @RequestParam("client_id")                String clientId,
            @RequestParam("redirect_uri")             String redirectUri,
            @RequestParam(value = "state",            required = false) String state,
            @RequestParam(value = "code_challenge",   required = false) String codeChallenge,
            @RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod) {

        String code = UUID.randomUUID().toString().replace("-", "");
        authCodes.put(code, clientId);

        StringBuilder location = new StringBuilder(redirectUri)
                .append("?code=").append(code);
        if (state != null) location.append("&state=").append(state);

        return ResponseEntity.status(302)
                .header("Location", location.toString())
                .build();
    }

    /** Token endpoint — exchanges authorization code for a dummy access token. */
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam("grant_type")               String grantType,
            @RequestParam(value = "code",             required = false) String code,
            @RequestParam(value = "redirect_uri",     required = false) String redirectUri,
            @RequestParam(value = "client_id",        required = false) String clientId,
            @RequestParam(value = "code_verifier",    required = false) String codeVerifier) {

        if (!"authorization_code".equals(grantType) || code == null || !authCodes.containsKey(code)) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_grant"));
        }

        authCodes.remove(code);

        return ResponseEntity.ok(Map.of(
                "access_token", UUID.randomUUID().toString(),
                "token_type",   "bearer",
                "expires_in",   86400
        ));
    }
}
