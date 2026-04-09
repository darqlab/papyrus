package io.darqlab.papyrus.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.darqlab.papyrus.api.controller.dto.SearchRequest;
import io.darqlab.papyrus.api.service.DocumentService;
import io.darqlab.papyrus.core.domain.DocumentChunk;
import io.darqlab.papyrus.core.domain.IngestionStatus;
import io.darqlab.papyrus.core.domain.SearchResult;
import io.darqlab.papyrus.core.domain.Source;
import io.darqlab.papyrus.core.service.EmbeddingService;
import io.darqlab.papyrus.pipeline.store.VectorStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pure controller unit tests — no Spring context, no DB.
 * Uses standaloneSetup + Mockito mocks.
 */
class DocumentControllerTest {

    private MockMvc mvc;
    private MockMvc searchMvc;

    private DocumentService documentService;
    private VectorStoreService vectorStoreService;
    private EmbeddingService embeddingService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();  // for Instant serialization

    @BeforeEach
    void setup() {
        documentService    = mock(DocumentService.class);
        vectorStoreService = mock(VectorStoreService.class);
        embeddingService   = mock(EmbeddingService.class);

        mvc = MockMvcBuilders.standaloneSetup(
                new DocumentController(documentService, vectorStoreService))
                .build();

        searchMvc = MockMvcBuilders.standaloneSetup(
                new SearchController(embeddingService, vectorStoreService))
                .build();
    }

    // ── POST /api/documents ───────────────────────────────────────────────────

    @Test
    void upload_validFile_returns200WithResult() throws Exception {
        UUID sourceId = UUID.randomUUID();
        when(documentService.ingest(any(), eq("report.txt"), eq("eng")))
                .thenReturn(new DocumentService.IngestionResult(sourceId, "report.txt", 5));

        MockMultipartFile file = new MockMultipartFile(
                "file", "report.txt", "text/plain", "Hello Papyrus".getBytes());

        mvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceId").value(sourceId.toString()))
                .andExpect(jsonPath("$.chunkCount").value(5))
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    void upload_emptyFile_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]);

        mvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/documents ────────────────────────────────────────────────────

    @Test
    void listDocuments_returnsSources() throws Exception {
        UUID id = UUID.randomUUID();
        Source source = new Source(id, "doc.pdf", "application/pdf",
                IngestionStatus.DONE, Instant.now());
        when(vectorStoreService.listSources(20, 0)).thenReturn(List.of(source));

        mvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].filename").value("doc.pdf"))
                .andExpect(jsonPath("$[0].status").value("DONE"));
    }

    @Test
    void listDocuments_empty_returnsEmptyArray() throws Exception {
        when(vectorStoreService.listSources(anyInt(), anyInt())).thenReturn(List.of());

        mvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    // ── GET /api/documents/{id} ────────────────────────────────────────────────

    @Test
    void getDocument_found_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        Source source = new Source(id, "doc.pdf", "application/pdf",
                IngestionStatus.DONE, Instant.now());
        when(vectorStoreService.listSources(Integer.MAX_VALUE, 0)).thenReturn(List.of(source));

        mvc.perform(get("/api/documents/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void getDocument_notFound_returns404() throws Exception {
        when(vectorStoreService.listSources(anyInt(), anyInt())).thenReturn(List.of());

        mvc.perform(get("/api/documents/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // ── POST /api/search ─────────────────────────────────────────────────────

    @Test
    void search_validQuery_returnsResults() throws Exception {
        UUID sourceId = UUID.randomUUID();
        DocumentChunk chunk = new DocumentChunk(UUID.randomUUID(), sourceId, 0, null,
                "Papyrus extracts text", 5, null, Instant.now());
        SearchResult result = new SearchResult(chunk, 0.92, "report.pdf");

        when(embeddingService.embed("papyrus")).thenReturn(Collections.nCopies(1024, 0.1f));
        when(vectorStoreService.searchByVector(any(), eq(5), isNull()))
                .thenReturn(List.of(result));

        SearchRequest req = new SearchRequest("papyrus", 5, null);
        searchMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Papyrus extracts text"))
                .andExpect(jsonPath("$[0].sourceFilename").value("report.pdf"))
                .andExpect(jsonPath("$[0].score").value(0.92));
    }

    @Test
    void search_blankQuery_returns400() throws Exception {
        SearchRequest req = new SearchRequest("", 5, null);
        searchMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
