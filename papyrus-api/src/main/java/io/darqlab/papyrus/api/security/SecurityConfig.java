package io.darqlab.papyrus.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("!dev")
public class SecurityConfig {

    @Value("${zitadel.issuer-uri:}")
    private String issuerUri;

    @Value("${zitadel.client-id:}")
    private String clientId;

    @Value("${zitadel.project-id:}")
    private String projectId;

    private final ZitadelRoleConverter roleConverter;

    public SecurityConfig(ZitadelRoleConverter roleConverter) {
        this.roleConverter = roleConverter;
    }

    /** REST API chain — Bearer JWT only, returns 401/403 (no browser redirect). */
    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )
            .exceptionHandling(ex -> ex
                .accessDeniedHandler((req, res, denied) -> res.sendError(403))
            );
        return http.build();
    }

    /** Browser chain — OAuth2 Login via Zitadel, session-based for page navigation. */
    @Bean
    @Order(2)
    public SecurityFilterChain browserFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/css/**", "/js/**", "/fonts/**", "/favicon.ico", "/assets/**", "/*.css", "/*.js").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/manage/**").hasRole("ADMIN")
                .requestMatchers("/ingest/**").hasAnyRole("ADMIN", "CONTRIBUTOR")
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .accessDeniedHandler((req, res, denied) -> res.sendRedirect("/chat"))
            )
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo
                    .userAuthoritiesMapper(authorities -> {
                        Set<GrantedAuthority> mapped = new HashSet<>(authorities);
                        for (GrantedAuthority authority : authorities) {
                            if (authority instanceof OidcUserAuthority oidcAuth) {
                                String claimKey = "urn:zitadel:iam:org:project:" + projectId + ":roles";
                                @SuppressWarnings("unchecked")
                                Map<String, Object> roles = (Map<String, Object>)
                                        oidcAuth.getIdToken().getClaim(claimKey);
                                if (roles != null && !roles.isEmpty()) {
                                    roles.keySet().stream()
                                         .map(r -> new SimpleGrantedAuthority("ROLE_" + r.toUpperCase()))
                                         .forEach(mapped::add);
                                } else {
                                    mapped.add(new SimpleGrantedAuthority("ROLE_READER"));
                                }
                            }
                        }
                        return mapped;
                    })
                )
            );
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roleConverter);
        return converter;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnExpression("!'${zitadel.issuer-uri:}'.isBlank()")
    public JwtDecoder jwtDecoder() {
        return JwtDecoders.fromIssuerLocation(issuerUri);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnExpression("!'${zitadel.issuer-uri:}'.isBlank()")
    public ClientRegistrationRepository clientRegistrationRepository() {
        ClientRegistration zitadel = ClientRegistrations.fromOidcIssuerLocation(issuerUri)
                .registrationId("zitadel")
                .clientId(clientId)
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .build();
        return new InMemoryClientRegistrationRepository(zitadel);
    }
}
