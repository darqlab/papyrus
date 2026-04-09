package io.darqlab.papyrus.pipeline.store;

import io.darqlab.papyrus.core.domain.IngestionStatus;
import io.darqlab.papyrus.core.domain.SearchResult;
import io.darqlab.papyrus.pipeline.PipelineTestApplication;
import io.darqlab.papyrus.pipeline.store.entity.DocumentSourceEntity;
import io.darqlab.papyrus.pipeline.store.repository.DocumentSourceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = PipelineTestApplication.class)
@Testcontainers
class VectorStoreServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired
    VectorStoreService vectorStoreService;

    @Autowired
    DocumentSourceRepository sourceRepository;

    @Test
    void storeAndSearch_returnsRelevantChunks() {
        UUID sourceId = createSource("test-doc.pdf");

        List<String> contents = List.of(
                "Papyrus ingests documents and extracts text for semantic search.",
                "The chunking service splits text into overlapping windows.",
                "Voyage AI provides high-quality embeddings for retrieval."
        );
        List<List<Float>> embeddings = List.of(
                basisVector(1024, 0),
                basisVector(1024, 1),
                basisVector(1024, 2)
        );
        List<Integer> tokenCounts = List.of(12, 10, 11);

        vectorStoreService.storeChunks(sourceId, contents, embeddings, tokenCounts, null);

        // Search with vector identical to the first chunk's embedding
        List<SearchResult> results = vectorStoreService.searchByVector(
                basisVector(1024, 0), 3, null);

        assertFalse(results.isEmpty());
        // Top result should be the chunk with the most similar vector
        assertEquals(contents.get(0), results.get(0).chunk().content());
    }

    @Test
    void search_withSourceIdFilter_restrictesToSource() {
        UUID sourceA = createSource("doc-a.pdf");
        UUID sourceB = createSource("doc-b.pdf");

        vectorStoreService.storeChunks(sourceA,
                List.of("Content from document A"),
                List.of(basisVector(1024, 10)),
                List.of(5), null);

        vectorStoreService.storeChunks(sourceB,
                List.of("Content from document B"),
                List.of(basisVector(1024, 20)),
                List.of(5), null);

        List<SearchResult> results = vectorStoreService.searchByVector(
                basisVector(1024, 10), 10, sourceA);

        assertTrue(results.stream().allMatch(r -> r.chunk().sourceId().equals(sourceA)),
                "All results should belong to sourceA");
        assertEquals(1, results.size());
    }

    @Test
    void listSources_returnsStoredSources() {
        createSource("listed-doc.pdf");

        var sources = vectorStoreService.listSources(100, 0);

        assertFalse(sources.isEmpty());
    }

    @Test
    void deleteBySourceId_removesSourceAndChunks() {
        UUID sourceId = createSource("delete-me.pdf");
        vectorStoreService.storeChunks(sourceId,
                List.of("chunk to delete"),
                List.of(basisVector(1024, 5)),
                List.of(3), null);

        vectorStoreService.deleteBySourceId(sourceId);

        assertTrue(vectorStoreService.listSources(100, 0)
                .stream().noneMatch(s -> s.id().equals(sourceId)),
                "Source should be gone after deletion");
        assertTrue(vectorStoreService.searchByVector(basisVector(1024, 5), 10, sourceId).isEmpty(),
                "Chunks should be gone after deletion");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID createSource(String filename) {
        UUID id = UUID.randomUUID();
        DocumentSourceEntity entity = new DocumentSourceEntity(
                id, filename, "application/pdf", 1024L, "eng", IngestionStatus.DONE);
        sourceRepository.save(entity);
        return id;
    }

    /** Returns a unit basis vector (1.0 at {@code hotIndex}, 0.0 elsewhere). */
    private List<Float> basisVector(int dims, int hotIndex) {
        List<Float> vec = new ArrayList<>(dims);
        for (int i = 0; i < dims; i++) vec.add(i == hotIndex ? 1.0f : 0.0f);
        return vec;
    }
}
