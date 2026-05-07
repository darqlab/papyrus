package io.darqlab.papyrus.api.security;

import io.darqlab.papyrus.core.domain.IngestionJob;
import io.darqlab.papyrus.core.domain.JobStatus;
import io.darqlab.papyrus.pipeline.archive.ArchiveService;
import io.darqlab.papyrus.pipeline.job.IngestionJobService;
import io.darqlab.papyrus.pipeline.store.VectorStoreService;
import io.darqlab.papyrus.api.controller.DocumentController;
import io.darqlab.papyrus.api.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@ContextConfiguration(classes = TestSecurityApplication.class)
@Import({SecurityConfig.class, ZitadelRoleConverter.class})
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired MockMvc mvc;

    @MockitoBean VectorStoreService vectorStoreService;
    @MockitoBean IngestionJobService jobService;
    @MockitoBean DocumentService documentService;
    @MockitoBean ArchiveService archiveService;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void noAuth_upload_returns401() throws Exception {
        mvc.perform(multipart("/api/documents")
                    .file(new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes())))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "READER")
    void reader_upload_returns403() throws Exception {
        mvc.perform(multipart("/api/documents")
                    .file(new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes())))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CONTRIBUTOR")
    void contributor_upload_returns202() throws Exception {
        UUID jobId = UUID.randomUUID();
        IngestionJob job = new IngestionJob(jobId, JobStatus.RUNNING, 1, 0, 0, Instant.now(), Instant.now());
        when(documentService.findDuplicate(any())).thenReturn(Optional.empty());
        when(jobService.createJob(anyInt())).thenReturn(job);

        mvc.perform(multipart("/api/documents")
                    .file(new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes())))
            .andExpect(status().isAccepted());
    }

    @Test
    @WithMockUser(roles = "READER")
    void reader_delete_returns403() throws Exception {
        mvc.perform(delete("/api/documents/" + UUID.randomUUID()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_delete_notFound_returns404() throws Exception {
        when(documentService.delete(any())).thenReturn(false);

        mvc.perform(delete("/api/documents/" + UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }
}
