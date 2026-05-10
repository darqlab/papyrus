package io.darqlab.papyrus.mcp.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.darqlab.papyrus.core.domain.SearchResult;
import io.darqlab.papyrus.core.domain.Source;
import io.darqlab.papyrus.core.service.EmbeddingService;
import io.darqlab.papyrus.pipeline.store.VectorStoreService;
import io.darqlab.papyrus.pipeline.store.entity.DocumentSourceEntity;
import io.darqlab.papyrus.pipeline.store.repository.DocumentSourceRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class SearchTools {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final DocumentSourceRepository sourceRepository;
    private final ObjectMapper objectMapper;

    public SearchTools(EmbeddingService embeddingService,
                       VectorStoreService vectorStoreService,
                       DocumentSourceRepository sourceRepository,
                       ObjectMapper objectMapper) {
        this.embeddingService   = embeddingService;
        this.vectorStoreService = vectorStoreService;
        this.sourceRepository   = sourceRepository;
        this.objectMapper       = objectMapper;
    }

    @Tool(name = "search",
          description = "Semantically search the Papyrus document knowledge base. " +
                        "Returns ranked text chunks with relevance scores. " +
                        "Text wrapped in [STRUCK OUT: ...] represents content that is no longer " +
                        "applicable or has been superseded in the source document — treat it as " +
                        "a prior version, not as current policy.")
    public String search(
            @ToolParam(description = "Natural language search query")
            String query,
            @ToolParam(required = false,
                       description = "Maximum results to return (default: 5)")
            Integer topK,
            @ToolParam(required = false,
                       description = "Restrict to a specific document source UUID; omit to search all")
            String sourceId) {

        try {
            List<Float> queryVector = embeddingService.embed(query);
            int k   = topK != null ? topK : 5;
            UUID sid = sourceId != null ? UUID.fromString(sourceId) : null;

            List<SearchResult> results = vectorStoreService.searchByVector(queryVector, k, sid);

            List<Map<String, Object>> payload = results.stream()
                    .map(r -> Map.<String, Object>of(
                            "content",         r.chunk().content(),
                            "score",           r.score(),
                            "source_id",       r.chunk().sourceId().toString(),
                            "source_filename", r.sourceFilename(),
                            "chunk_index",     r.chunk().chunkIndex()
                    ))
                    .toList();

            return objectMapper.writeValueAsString(payload);

        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @Tool(name = "list_sources",
          description = "List all documents that have been ingested into the Papyrus knowledge base.")
    public String listSources(
            @ToolParam(required = false,
                       description = "Maximum results per page (default: 20)")
            Integer limit,
            @ToolParam(required = false,
                       description = "Pagination offset (default: 0)")
            Integer offset) {

        try {
            List<Source> sources = vectorStoreService.listSources(
                    limit  != null ? limit  : 20,
                    offset != null ? offset : 0);

            List<Map<String, Object>> payload = sources.stream()
                    .map(s -> Map.<String, Object>of(
                            "id",           s.id().toString(),
                            "filename",     s.filename(),
                            "content_type", s.contentType(),
                            "status",       s.status().name(),
                            "created_at",   s.createdAt().toString()
                    ))
                    .toList();

            return objectMapper.writeValueAsString(payload);

        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @Tool(name = "get_document",
          description = "Retrieve metadata for a specific ingested document by its source UUID.")
    public String getDocument(
            @ToolParam(description = "UUID of the document source")
            String sourceId) {

        try {
            UUID sid = UUID.fromString(sourceId);
            return sourceRepository.findById(sid)
                    .map(this::toPayload)
                    .orElse("{\"error\":\"Document not found\"}");

        } catch (IllegalArgumentException e) {
            return error("Invalid UUID: " + sourceId);
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String toPayload(DocumentSourceEntity e) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "id",           e.getId().toString(),
                    "filename",     e.getFilename(),
                    "content_type", e.getContentType(),
                    "status",       e.getStatus().name(),
                    "created_at",   e.getCreatedAt().toString()
            ));
        } catch (Exception ex) {
            return error(ex.getMessage());
        }
    }

    private String error(String message) {
        return "{\"error\":\"" + escape(message) + "\"}";
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "'");
    }
}
