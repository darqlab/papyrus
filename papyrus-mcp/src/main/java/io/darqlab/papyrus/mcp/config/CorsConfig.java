package io.darqlab.papyrus.mcp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS configuration for the MCP server.
 *
 * Allows claude.ai, Claude mobile, and Claude Desktop to reach the /mcp
 * Streamable HTTP endpoint from browser and app origins.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/mcp")
                .allowedOrigins(
                        "https://claude.ai",
                        "https://api.anthropic.com"
                )
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("mcp-session-id")
                .allowCredentials(false)
                .maxAge(3600);

        registry.addMapping("/.well-known/oauth-protected-resource")
                .allowedOrigins(
                        "https://claude.ai",
                        "https://api.anthropic.com"
                )
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
