package io.darqlab.papyrus.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.darqlab.papyrus.api.controller.dto.UrlIngestRequest;
import io.darqlab.papyrus.api.service.DocumentService;
import io.darqlab.papyrus.core.domain.IngestionJob;
import io.darqlab.papyrus.core.domain.JobStatus;
import io.darqlab.papyrus.pipeline.job.IngestionJobService;
import io.darqlab.papyrus.pipeline.store.VectorStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.time.Instant.now;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 6 controller unit tests — delete, URL ingest, batch, job status.
 */
class Phase6ControllerTest {

    private MockMvc docMvc;
    private MockMvc jobMvc;

    private DocumentService documentService;
    private VectorStoreService vectorStoreService;
    private IngestionJobService jobService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setup() {
        documentService    = mock(DocumentService.class);
        vectorStoreService = mock(VectorStoreService.class);
        jobService         = mock(IngestionJobService.class);

        docMvc = MockMvcBuilders.standaloneSetup(
                new DocumentController(documentService, vectorStoreService, jobService))
                .build();

        jobMvc = MockMvcBuilders.standaloneSetup(
                new JobController(jobService))
                .build();
    }

    // ── DELETE /api/documents/{id} ────────────────────────────────────────────

    @Test
    void delete_existingDocument_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        when(documentService.delete(id)).thenReturn(true);

        docMvc.perform(delete("/api/documents/" + id))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(documentService.delete(id)).thenReturn(false);

        docMvc.perform(delete("/api/documents/" + id))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/documents/url ───────────────────────────────────────────────

    @Test
    void ingestUrl_validRequest_returns200() throws Exception {
        UUID sourceId = UUID.randomUUID();
        when(documentService.ingestUrl(eq("https://example.com"), eq("eng")))
                .thenReturn(new DocumentService.IngestionResult(sourceId, "example.com.html", 8));

        UrlIngestRequest req = new UrlIngestRequest("https://example.com", "eng");
        docMvc.perform(post("/api/documents/url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceId").value(sourceId.toString()))
                .andExpect(jsonPath("$.chunkCount").value(8))
                .andExpect(jsonPath("$.filename").value("example.com.html"));
    }

    @Test
    void ingestUrl_blankUrl_returns400() throws Exception {
        UrlIngestRequest req = new UrlIngestRequest("", null);
        docMvc.perform(post("/api/documents/url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/documents/batch ─────────────────────────────────────────────

    @Test
    void batch_twoFiles_returnsBatchResponse() throws Exception {
        UUID jobId = UUID.randomUUID();
        IngestionJob job = new IngestionJob(jobId, JobStatus.QUEUED, 2, 0, 0, now(), now());
        when(jobService.createJob(2)).thenReturn(job);
        when(documentService.ingest(any(), anyString(), anyString()))
                .thenReturn(new DocumentService.IngestionResult(UUID.randomUUID(), "a.txt", 3))
                .thenReturn(new DocumentService.IngestionResult(UUID.randomUUID(), "b.txt", 5));

        MockMultipartFile f1 = new MockMultipartFile("files", "a.txt", "text/plain", "Hello".getBytes());
        MockMultipartFile f2 = new MockMultipartFile("files", "b.txt", "text/plain", "World".getBytes());

        docMvc.perform(multipart("/api/documents/batch").file(f1).file(f2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.processed").value(2))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.results.length()").value(2));
    }

    @Test
    void batch_noFiles_returns400() throws Exception {
        docMvc.perform(multipart("/api/documents/batch"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/jobs/{id} ────────────────────────────────────────────────────

    @Test
    void getJob_found_returnsStatus() throws Exception {
        UUID jobId = UUID.randomUUID();
        IngestionJob job = new IngestionJob(jobId, JobStatus.DONE, 5, 5, 0, now(), now());
        when(jobService.find(jobId)).thenReturn(Optional.of(job));

        jobMvc.perform(get("/api/jobs/" + jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.processed").value(5));
    }

    @Test
    void getJob_notFound_returns404() throws Exception {
        when(jobService.find(any())).thenReturn(Optional.empty());

        jobMvc.perform(get("/api/jobs/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
