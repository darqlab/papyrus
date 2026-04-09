package io.darqlab.papyrus.api.controller;

import io.darqlab.papyrus.api.controller.dto.SearchRequest;
import io.darqlab.papyrus.api.controller.dto.SearchResultResponse;
import io.darqlab.papyrus.core.service.EmbeddingService;
import io.darqlab.papyrus.pipeline.store.VectorStoreService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    public SearchController(EmbeddingService embeddingService,
                             VectorStoreService vectorStoreService) {
        this.embeddingService   = embeddingService;
        this.vectorStoreService = vectorStoreService;
    }

    /**
     * Semantic search.
     * <p>POST /api/search</p>
     */
    @PostMapping
    public ResponseEntity<List<SearchResultResponse>> search(@RequestBody SearchRequest request) {
        if (request.query() == null || request.query().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        int topK = request.topK() != null ? request.topK() : 5;
        List<Float> queryVector = embeddingService.embed(request.query());

        List<SearchResultResponse> results = vectorStoreService
                .searchByVector(queryVector, topK, request.sourceId())
                .stream()
                .map(r -> new SearchResultResponse(
                        r.chunk().content(),
                        r.score(),
                        r.chunk().sourceId(),
                        r.sourceFilename(),
                        r.chunk().chunkIndex()))
                .toList();

        return ResponseEntity.ok(results);
    }
}
