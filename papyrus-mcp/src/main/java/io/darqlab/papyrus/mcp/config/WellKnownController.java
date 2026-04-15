package io.darqlab.papyrus.mcp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Serves OAuth 2.0 Protected Resource Metadata (RFC 9728) at the well-known endpoint.
 *
 * claude.ai and other MCP clients probe this endpoint before connecting to determine
 * whether the server requires OAuth. Returning an empty bearer_methods_supported list
 * signals that no bearer token auth is required and the client may proceed directly
 * with the MCP protocol.
 *
 * Without this endpoint, claude.ai's connector setup flow fails with
 * "Couldn't reach the MCP server" even when CORS is correctly configured.
 */
@RestController
public class WellKnownController {

    private final String resourceUrl;

    public WellKnownController(
            @Value("${papyrus.mcp.public-url:https://mcp-papyrus.darqlab.net/mcp}") String resourceUrl) {
        this.resourceUrl = resourceUrl;
    }

    @GetMapping(value = "/.well-known/oauth-protected-resource", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> oauthProtectedResource() {
        return ResponseEntity.ok(Map.of(
                "resource", resourceUrl,
                "bearer_methods_supported", List.of()
        ));
    }
}
