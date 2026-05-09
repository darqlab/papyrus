package io.darqlab.papyrus.api.controller;

import io.darqlab.papyrus.api.security.SecurityConfig;
import io.darqlab.papyrus.api.security.ZitadelManagementClient;
import io.darqlab.papyrus.api.security.ZitadelManagementTokenProvider;
import io.darqlab.papyrus.api.security.ZitadelRoleConverter;
import io.darqlab.papyrus.api.security.ZitadelUser;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserController.class)
@ContextConfiguration(classes = TestSecurityApplication.class)
@Import({SecurityConfig.class, ZitadelRoleConverter.class})
@ActiveProfiles("test")
class AdminUserControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean ZitadelManagementClient managementClient;
    @MockitoBean ZitadelManagementTokenProvider tokenProvider;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean ClientRegistrationRepository clientRegistrationRepository;

    private static final ZitadelUser SAMPLE_USER = new ZitadelUser(
            "user-1", "Jane Doe", "jane@example.com",
            "READER", "grant-1", Instant.parse("2026-05-01T00:00:00Z"), null
    );

    // ── Unauthenticated ────────────────────────────────────────────────────

    @Test
    void noAuth_listUsers_returns401() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void noAuth_createUser_returns401() throws Exception {
        mvc.perform(post("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"a@b.com\",\"role\":\"READER\"}"))
            .andExpect(status().isUnauthorized());
    }

    // ── READER ─────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "READER")
    void reader_listUsers_returns403() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "READER")
    void reader_removeUser_returns403() throws Exception {
        mvc.perform(delete("/api/admin/users/user-1").param("grantId", "grant-1"))
            .andExpect(status().isForbidden());
    }

    // ── CONTRIBUTOR ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "CONTRIBUTOR")
    void contributor_listUsers_returns403() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());
    }

    // ── ADMIN ──────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_listUsers_returns200() throws Exception {
        when(managementClient.listProjectUsers()).thenReturn(List.of(SAMPLE_USER));

        mvc.perform(get("/api/admin/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].userId").value("user-1"))
            .andExpect(jsonPath("$[0].email").value("jane@example.com"))
            .andExpect(jsonPath("$[0].role").value("READER"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_inviteUser_returns201() throws Exception {
        doNothing().when(managementClient).inviteUser(anyString(), anyString(), anyString(), anyString());

        mvc.perform(post("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Jane\",\"lastName\":\"Doe\",\"email\":\"jane@example.com\",\"role\":\"READER\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_changeRole_returns200() throws Exception {
        doNothing().when(managementClient).changeRole(anyString(), anyString());

        mvc.perform(put("/api/admin/users/user-1/role")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"grantId\":\"grant-1\",\"role\":\"ADMIN\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_removeUser_returns204() throws Exception {
        doNothing().when(managementClient).removeUser(anyString());

        mvc.perform(delete("/api/admin/users/user-1").param("grantId", "grant-1"))
            .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_listUsers_empty_returns200() throws Exception {
        when(managementClient.listProjectUsers()).thenReturn(List.of());

        mvc.perform(get("/api/admin/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
    }
}
