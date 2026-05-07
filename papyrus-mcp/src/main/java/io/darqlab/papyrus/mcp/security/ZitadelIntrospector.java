package io.darqlab.papyrus.mcp.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionAuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ZitadelIntrospector implements OpaqueTokenIntrospector {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String introspectionUri;
    private final String clientId;
    private final String clientSecret;
    private final String projectId;

    public ZitadelIntrospector(
            @Value("${zitadel.introspection-uri}") String introspectionUri,
            @Value("${zitadel.introspection-client-id}") String clientId,
            @Value("${zitadel.introspection-client-secret}") String clientSecret,
            @Value("${zitadel.project-id:}") String projectId) {
        this.introspectionUri = introspectionUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.projectId = projectId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("token", token);

        Map<String, Object> claims;
        try {
            claims = restTemplate.postForObject(
                introspectionUri, new HttpEntity<>(body, headers), Map.class);
        } catch (Exception e) {
            throw new OAuth2IntrospectionException("Introspection request failed: " + e.getMessage(), e);
        }

        if (claims == null || !Boolean.TRUE.equals(claims.get("active"))) {
            throw new OAuth2IntrospectionException("Token is not active");
        }

        String claimKey = "urn:zitadel:iam:org:project:" + projectId + ":roles";
        Map<String, Object> roles = (Map<String, Object>) claims.get(claimKey);

        Set<GrantedAuthority> authorities = new HashSet<>();
        if (roles != null && !roles.isEmpty()) {
            roles.keySet().stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.toUpperCase()))
                .forEach(authorities::add);
        } else {
            authorities.add(new SimpleGrantedAuthority("ROLE_READER"));
        }

        // OAuth2IntrospectionAuthenticatedPrincipal expects Instant for time claims,
        // but Zitadel returns them as integers (epoch seconds).
        Map<String, Object> convertedClaims = new HashMap<>(claims);
        for (String field : List.of("exp", "iat", "nbf")) {
            if (convertedClaims.get(field) instanceof Number n) {
                convertedClaims.put(field, Instant.ofEpochSecond(n.longValue()));
            }
        }

        String subject = (String) convertedClaims.getOrDefault("sub", "unknown");
        return new OAuth2IntrospectionAuthenticatedPrincipal(subject, convertedClaims, authorities);
    }
}
