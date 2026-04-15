package io.darqlab.papyrus.mcp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Serves OAuth 2.0 discovery documents required by claude.ai remote MCP connectors.
 *
 * claude.ai probes these endpoints before connecting to determine whether OAuth
 * is required and where the authorization endpoints are.
 *
 * RFC 9728 — Protected Resource Metadata  → /.well-known/oauth-protected-resource
 * RFC 8414 — Authorization Server Metadata → /.well-known/oauth-authorization-server
 */
@RestController
public class WellKnownController {

    private final String baseUrl;
    private final String resourceUrl;

    public WellKnownController(
            @Value("${papyrus.mcp.base-url:https://mcp-papyrus.darqlab.net}") String baseUrl,
            @Value("${papyrus.mcp.public-url:https://mcp-papyrus.darqlab.net/mcp}") String resourceUrl) {
        this.baseUrl     = baseUrl;
        this.resourceUrl = resourceUrl;
    }

    /** RFC 9728 — tells the client where the authorization server is. */
    @GetMapping(value = "/.well-known/oauth-protected-resource", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> protectedResource() {
        return ResponseEntity.ok(Map.of(
                "resource",              resourceUrl,
                "authorization_servers", List.of(baseUrl)
        ));
    }

    /** RFC 8414 — authorization server capability discovery. */
    @GetMapping(value = "/.well-known/oauth-authorization-server", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> authorizationServer() {
        return ResponseEntity.ok(Map.of(
                "issuer",                                baseUrl,
                "authorization_endpoint",               baseUrl + "/oauth/authorize",
                "token_endpoint",                       baseUrl + "/oauth/token",
                "registration_endpoint",                baseUrl + "/oauth/register",
                "response_types_supported",             List.of("code"),
                "grant_types_supported",                List.of("authorization_code"),
                "code_challenge_methods_supported",     List.of("S256"),
                "token_endpoint_auth_methods_supported", List.of("none")
        ));
    }
}
