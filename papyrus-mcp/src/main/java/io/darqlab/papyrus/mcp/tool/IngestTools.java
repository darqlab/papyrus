package io.darqlab.papyrus.mcp.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.darqlab.papyrus.mcp.service.IngestionOrchestrator;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Component
public class IngestTools {

    private final IngestionOrchestrator orchestrator;
    private final ObjectMapper objectMapper;

    public IngestTools(IngestionOrchestrator orchestrator, ObjectMapper objectMapper) {
        this.orchestrator = orchestrator;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "ingest_document",
          description = "Ingest a document into the Papyrus knowledge base. " +
                        "Supports PDF, DOCX, HTML, TXT, MD, and CSV. " +
                        "The file content must be base64-encoded.")
    public String ingestDocument(
            @ToolParam(description = "Original filename including extension, e.g. report.pdf")
            String filename,
            @ToolParam(description = "Base64-encoded file content")
            String content,
            @ToolParam(required = false,
                       description = "Document language for processing (default: eng)")
            String language) {

        try {
            byte[] bytes = Base64.getDecoder().decode(content);
            String lang  = language != null ? language : "eng";
            IngestionOrchestrator.IngestionResult result = orchestrator.ingest(bytes, filename, lang);

            return objectMapper.writeValueAsString(Map.of(
                    "source_id",  result.sourceId().toString(),
                    "chunk_count", result.chunkCount(),
                    "status",     "DONE"
            ));

        } catch (IllegalArgumentException e) {
            return error("Invalid base64 content: " + e.getMessage());
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @Tool(name = "ingest_url",
          description = "Fetch a web page by URL and ingest it into the Papyrus knowledge base.")
    public String ingestUrl(
            @ToolParam(description = "Fully qualified URL to fetch, e.g. https://example.com/article")
            String url,
            @ToolParam(required = false,
                       description = "Document language for processing (default: eng)")
            String language) {

        try {
            String lang = language != null ? language : "eng";
            IngestionOrchestrator.IngestionResult result = orchestrator.ingestUrl(url, lang);
            return objectMapper.writeValueAsString(Map.of(
                    "source_id",   result.sourceId().toString(),
                    "chunk_count", result.chunkCount(),
                    "url",         url,
                    "status",      "DONE"
            ));
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @Tool(name = "delete_source",
          description = "Permanently delete an ingested document and all its chunks from the knowledge base.")
    public String deleteSource(
            @ToolParam(description = "UUID of the document source to delete")
            String sourceId) {

        try {
            UUID sid = UUID.fromString(sourceId);
            boolean deleted = orchestrator.delete(sid);
            if (deleted) {
                return "{\"status\":\"deleted\",\"source_id\":\"" + sourceId + "\"}";
            } else {
                return "{\"error\":\"Document not found: " + sourceId + "\"}";
            }
        } catch (IllegalArgumentException e) {
            return error("Invalid UUID: " + sourceId);
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    private String error(String message) {
        return "{\"error\":\"" + escape(message) + "\"}";
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "'");
    }
}
