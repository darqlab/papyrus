package io.darqlab.papyrus.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.darqlab.papyrus.core.domain.ChatTurn;
import io.darqlab.papyrus.core.domain.SearchResult;
import io.darqlab.papyrus.core.service.ChatService;
import io.darqlab.papyrus.core.service.EmbeddingService;
import io.darqlab.papyrus.pipeline.config.PromptLoader;
import io.darqlab.papyrus.pipeline.store.VectorStoreService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class ChatController {

    private final ChatService chatService;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final ObjectMapper mapper;
    private final String systemPrompt;

    public ChatController(
            ChatService chatService,
            EmbeddingService embeddingService,
            VectorStoreService vectorStoreService,
            ObjectMapper mapper) {
        this.chatService = chatService;
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
        this.mapper = mapper;
        this.systemPrompt = PromptLoader.load("CHAT_PROMPT_FILE", "prompts/chat-system.md");
    }

    record ChatMessage(String role, String content) {}
    record ChatRequest(List<ChatMessage> messages, String sourceId) {}

    @PostMapping(value = "/api/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);

        Thread.ofVirtual().start(() -> {
            try {
                // ── RAG: embed last user message and retrieve top-5 chunks ──
                String userQuery = request.messages().reversed().stream()
                        .filter(m -> "user".equals(m.role()))
                        .map(ChatMessage::content)
                        .findFirst()
                        .orElse("");

                // Parse optional sourceId filter
                UUID sourceUUID = null;
                if (request.sourceId() != null && !request.sourceId().isBlank()) {
                    try { sourceUUID = UUID.fromString(request.sourceId()); }
                    catch (IllegalArgumentException ignored) {}
                }

                List<SearchResult> searchResults = List.of();
                String effectiveSystemPrompt = systemPrompt;
                if (!userQuery.isBlank()) {
                    List<Float> vector = embeddingService.embed(userQuery);
                    searchResults = vectorStoreService.searchByVector(vector, 5, sourceUUID);
                    if (!searchResults.isEmpty()) {
                        var ctx = new StringBuilder("\n\n--- Relevant document excerpts ---\n\n");
                        for (var r : searchResults) {
                            String label = r.archiveFilename() != null ? r.archiveFilename() : r.sourceFilename();
                            ctx.append("Source: ").append(label).append('\n');
                            ctx.append(r.chunk().content()).append("\n\n");
                        }
                        ctx.append("--- End of excerpts ---");
                        effectiveSystemPrompt += ctx;
                    }
                }

                // ── Build ChatTurn list and stream ────────────────────────
                List<ChatTurn> turns = request.messages().stream()
                        .map(m -> new ChatTurn(m.role(), m.content()))
                        .toList();

                try (var tokenStream = chatService.streamChat(turns, effectiveSystemPrompt)
                        .onClose(() -> {})) {
                    tokenStream.forEach(token -> {
                        try {
                            emitter.send(SseEmitter.event().data(mapper.writeValueAsString(token)));
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
                }

                // Emit token usage
                emitter.send(SseEmitter.event().name("usage")
                        .data(mapper.writeValueAsString(
                                Map.of("inputTokens", inputTokens[0], "outputTokens", outputTokens[0]))));

                // Emit sources before done
                if (!searchResults.isEmpty()) {
                    List<Map<String, String>> sources = new ArrayList<>();
                    for (var r : searchResults) {
                        Map<String, String> entry = new LinkedHashMap<>();
                        entry.put("sourceId", r.chunk().sourceId().toString());
                        entry.put("filename", r.archiveFilename() != null ? r.archiveFilename() : r.sourceFilename());
                        entry.put("archiveFilename", r.archiveFilename());
                        String excerpt = r.chunk().content();
                        if (excerpt.length() > 200) excerpt = excerpt.substring(0, 200) + "…";
                        entry.put("excerpt", excerpt);
                        sources.add(entry);
                    }
                    emitter.send(SseEmitter.event().name("sources")
                            .data(mapper.writeValueAsString(sources)));
                }

                emitter.send(SseEmitter.event().name("done").data(""));
                emitter.complete();

            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(mapper.writeValueAsString(e.getMessage())));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
