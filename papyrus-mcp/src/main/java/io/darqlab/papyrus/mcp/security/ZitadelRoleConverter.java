package io.darqlab.papyrus.mcp.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
public class ZitadelRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final String projectId;

    public ZitadelRoleConverter(@Value("${zitadel.project-id:}") String projectId) {
        this.projectId = projectId;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        String claimKey = "urn:zitadel:iam:org:project:" + projectId + ":roles";
        Map<String, Object> roles = jwt.getClaim(claimKey);
        if (roles == null || roles.isEmpty()) {
            return List.of(new SimpleGrantedAuthority("ROLE_READER"));
        }
        return roles.keySet().stream()
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .toList();
    }
}
