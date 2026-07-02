package io.darqlab.papyrus.api.controller;

import io.darqlab.papyrus.api.security.SecurityConfig;
import io.darqlab.papyrus.api.security.ZitadelManagementClient;
import io.darqlab.papyrus.api.security.ZitadelManagementTokenProvider;
import io.darqlab.papyrus.api.security.ZitadelMcpClient;
import io.darqlab.papyrus.api.security.ZitadelMcpToken;
import io.darqlab.papyrus.api.security.ZitadelRoleConverter;
import io.darqlab.papyrus.api.security.TestSecurityApplication; // shared test bootstrap
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminMcpClientController.class)
@ContextConfiguration(classes = TestSecurityApplication.class)
@Import({SecurityConfig.class, ZitadelRoleConverter.class})
@ActiveProfiles("test")
class AdminMcpClientControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean ZitadelManagementClient managementClient;
    @MockitoBean ZitadelManagementTokenProvider tokenProvider;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean ClientRegistrationRepository clientRegistrationRepository;

    private static final ZitadelMcpClient SAMPLE_CLIENT = new ZitadelMcpClient(
            "user-1", "claude-code-darqlab", "READER", "grant-1", Instant.parse("2026-07-01T00:00:00Z")
    );

    // ── Unauthenticated ────────────────────────────────────────────────────

    @Test
    void noAuth_listClients_returns401() throws Exception {
        mvc.perform(get("/api/admin/mcp-clients")).andExpect(status().isUnauthorized());
    }

    @Test
    void noAuth_createClient_returns401() throws Exception {
        mvc.perform(post("/api/admin/mcp-clients")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"claude-code\",\"role\":\"READER\"}"))
            .andExpect(status().isUnauthorized());
    }

    // ── READER ─────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "READER")
    void reader_listClients_returns403() throws Exception {
        mvc.perform(get("/api/admin/mcp-clients")).andExpect(status().isForbidden());
    }

    // ── CONTRIBUTOR ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "CONTRIBUTOR")
    void contributor_listClients_returns403() throws Exception {
        mvc.perform(get("/api/admin/mcp-clients")).andExpect(status().isForbidden());
    }

    // ── ADMIN ──────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_listClients_returns200() throws Exception {
        when(managementClient.listMcpClients()).thenReturn(List.of(SAMPLE_CLIENT));

        mvc.perform(get("/api/admin/mcp-clients"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].userId").value("user-1"))
            .andExpect(jsonPath("$[0].name").value("claude-code-darqlab"))
            .andExpect(jsonPath("$[0].role").value("READER"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_createClient_returns201WithToken() throws Exception {
        when(managementClient.createMcpClient(anyString(), anyString()))
                .thenReturn(new ZitadelMcpToken("user-1", "pat-1", "raw-token-value"));

        mvc.perform(post("/api/admin/mcp-clients")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"claude-code-darqlab\",\"role\":\"READER\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.userId").value("user-1"))
            .andExpect(jsonPath("$.token").value("raw-token-value"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_revokeClient_returns204() throws Exception {
        doNothing().when(managementClient).deleteMcpClient(anyString());

        mvc.perform(delete("/api/admin/mcp-clients/user-1"))
            .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_listClients_empty_returns200() throws Exception {
        when(managementClient.listMcpClients()).thenReturn(List.of());

        mvc.perform(get("/api/admin/mcp-clients"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
    }
}
