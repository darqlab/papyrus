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
 * Exposes OAuth 2.0 Authorization Server Metadata (RFC 8414) so that MCP
 * clients (e.g. claude.ai) can discover Zitadel as the authorization server.
 *
 * Clients discover this at /.well-known/oauth-authorization-server, then
 * redirect the user to Zitadel for login. The resulting access token is sent
 * as a Bearer token to /mcp and validated by ZitadelIntrospector.
 */
@Configuration
public class OAuthController {

    @Value("${zitadel.issuer-uri:}")
    private String issuerUri;

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
            .build();
    }
}
