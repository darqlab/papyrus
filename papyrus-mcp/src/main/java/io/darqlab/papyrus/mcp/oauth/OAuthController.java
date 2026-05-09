package io.darqlab.papyrus.mcp.oauth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.List;
import java.util.Map;

/**
 * Exposes OAuth 2.0 discovery documents required by MCP clients (e.g. claude.ai):
 *
 * RFC 8414 — Authorization Server Metadata → /.well-known/oauth-authorization-server
 *   Points clients to Zitadel as the auth server.
 *
 * RFC 9728 — Protected Resource Metadata   → /.well-known/oauth-protected-resource
 *   Tells clients which authorization server protects /mcp.
 *
 * RouterFunction is used instead of @GetMapping to avoid Spring MVC dot-path
 * mapping issues with /.well-known/* paths.
 */
@Configuration
public class OAuthController {

    @Value("${zitadel.issuer-uri:}")
    private String issuerUri;

    @Value("${papyrus.mcp.public-url:https://papyrusmcp.arqhive.systems/mcp}")
    private String resourceUrl;

    @Bean
    public RouterFunction<ServerResponse> oauthMetadataRouter() {
        return RouterFunctions.route()
            .GET("/.well-known/oauth-authorization-server", request -> {
                if (issuerUri.isBlank()) {
                    return ServerResponse.notFound().build();
                }
                Map<String, Object> metadata = Map.of(
                    "issuer",                           issuerUri,
                    "authorization_endpoint",           issuerUri + "/oauth/v2/authorize",
                    "token_endpoint",                   issuerUri + "/oauth/v2/token",
                    "response_types_supported",         List.of("code"),
                    "grant_types_supported",            List.of("authorization_code", "refresh_token"),
                    "code_challenge_methods_supported", List.of("S256"),
                    "scopes_supported",                 List.of("openid", "profile", "email")
                );
                return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(metadata);
            })
            .GET("/.well-known/oauth-protected-resource", request -> {
                if (issuerUri.isBlank()) {
                    return ServerResponse.notFound().build();
                }
                Map<String, Object> metadata = Map.of(
                    "resource",              resourceUrl,
                    "authorization_servers", List.of(issuerUri)
                );
                return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(metadata);
            })
            .build();
    }
}
