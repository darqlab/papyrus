package io.darqlab.papyrus.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.darqlab.papyrus.core.domain.IngestionStatus;
import io.darqlab.papyrus.core.service.EmbeddingService;
import io.darqlab.papyrus.core.util.TokenEstimator;
import io.darqlab.papyrus.extractor.FormatRouter;
import io.darqlab.papyrus.mcp.service.IngestionOrchestrator;
import io.darqlab.papyrus.mcp.tool.IngestTools;
import io.darqlab.papyrus.mcp.tool.SearchTools;
import io.darqlab.papyrus.pipeline.chunking.ChunkingService;
import io.darqlab.papyrus.pipeline.config.ChunkingStrategy;
import io.darqlab.papyrus.pipeline.config.PapyrusProperties;
import io.darqlab.papyrus.pipeline.store.VectorStoreService;
import io.darqlab.papyrus.pipeline.store.entity.DocumentSourceEntity;
import io.darqlab.papyrus.pipeline.store.repository.DocumentSourceRepository;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MCP tool methods — no Spring context, no DB, uses Mockito.
 */
class McpToolsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── IngestTools error handling ────────────────────────────────────────────

    @Test
    void ingestDocument_invalidBase64_returnsError() {
        IngestTools tools = new IngestTools(null, objectMapper);

        String result = tools.ingestDocument("test.txt", "NOT_VALID_BASE64!!!", null);

        assertTrue(result.contains("error"), "Expected error JSON, got: " + result);
    }

    @Test
    void ingestDocument_nullContent_returnsError() {
        IngestTools tools = new IngestTools(null, objectMapper);

        String result = tools.ingestDocument("test.txt", null, null);

        assertTrue(result.contains("error"), "Expected error JSON, got: " + result);
    }

    // ── SearchTools error handling ─────────────────────────────────────────────

    @Test
    void getDocument_invalidUuid_returnsError() {
        SearchTools tools = new SearchTools(null, null, null, objectMapper);

        String result = tools.getDocument("not-a-uuid");

        assertTrue(result.contains("error"), "Expected error JSON, got: " + result);
    }

    // ── IngestionOrchestrator ─────────────────────────────────────────────────

    @Test
    void orchestrator_ingestPlainText_storesChunks() throws Exception {
        PapyrusProperties props = new PapyrusProperties(
                new PapyrusProperties.EmbeddingProperties("voyage",
                        new PapyrusProperties.VoyageProperties("key", "voyage-3-lite"),
                        new PapyrusProperties.OllamaProperties("http://localhost:11434", "nomic-embed-text")),
                new PapyrusProperties.ChunkingProperties(ChunkingStrategy.PARAGRAPH, 512, 64),
                new PapyrusProperties.SearchProperties(5),
                new PapyrusProperties.OcrProperties(
                        new PapyrusProperties.CorrectionProperties(false, null, null)));

        ChunkingService chunkingService = new ChunkingService(props);
        FormatRouter formatRouter = FormatRouter.withDefaultExtractors();

        EmbeddingService embeddingService = mock(EmbeddingService.class);
        when(embeddingService.embed(any())).thenReturn(Collections.nCopies(512, 0.1f));

        AtomicInteger storedCount = new AtomicInteger(0);
        VectorStoreService vectorStore = mock(VectorStoreService.class);
        doAnswer(inv -> { storedCount.set(((java.util.List<?>) inv.getArgument(1)).size()); return null; })
                .when(vectorStore).storeChunks(any(), any(), any(), any(), any());

        DocumentSourceRepository sourceRepo = mock(DocumentSourceRepository.class);
        when(sourceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IngestionOrchestrator orchestrator = new IngestionOrchestrator(
                formatRouter, chunkingService, embeddingService, vectorStore, sourceRepo);

        byte[] content = "Hello Papyrus.\n\nThis is a test document for ingestion pipeline testing.".getBytes();
        IngestionOrchestrator.IngestionResult result = orchestrator.ingest(content, "test.txt", "eng");

        assertNotNull(result.sourceId());
        assertTrue(result.chunkCount() >= 1, "Expected at least one chunk");
        assertEquals(storedCount.get(), result.chunkCount());

        verify(vectorStore).storeChunks(eq(result.sourceId()), any(), any(), any(), any());
        verify(sourceRepo, atLeast(2)).save(any()); // initial PROCESSING + final DONE
    }

    @Test
    void orchestrator_ingestEmptyDocument_returnsZeroChunks() throws Exception {
        PapyrusProperties props = new PapyrusProperties(
                new PapyrusProperties.EmbeddingProperties("voyage",
                        new PapyrusProperties.VoyageProperties("key", "voyage-3-lite"),
                        new PapyrusProperties.OllamaProperties("http://localhost:11434", "nomic-embed-text")),
                new PapyrusProperties.ChunkingProperties(ChunkingStrategy.PARAGRAPH, 512, 64),
                new PapyrusProperties.SearchProperties(5),
                new PapyrusProperties.OcrProperties(
                        new PapyrusProperties.CorrectionProperties(false, null, null)));

        ChunkingService chunkingService = new ChunkingService(props);
        FormatRouter formatRouter = FormatRouter.withDefaultExtractors();
        VectorStoreService vectorStore = mock(VectorStoreService.class);

        DocumentSourceRepository sourceRepo = mock(DocumentSourceRepository.class);
        when(sourceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IngestionOrchestrator orchestrator = new IngestionOrchestrator(
                formatRouter, chunkingService, mock(EmbeddingService.class), vectorStore, sourceRepo);

        byte[] content = "   ".getBytes();
        IngestionOrchestrator.IngestionResult result = orchestrator.ingest(content, "empty.txt", "eng");

        assertEquals(0, result.chunkCount());
        verifyNoInteractions(vectorStore);
    }
}
