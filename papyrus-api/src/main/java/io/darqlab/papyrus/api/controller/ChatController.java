package io.darqlab.papyrus.api.controller;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.darqlab.papyrus.core.service.EmbeddingService;
import io.darqlab.papyrus.pipeline.store.VectorStoreService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

@RestController
public class ChatController {

    private final AnthropicClient anthropic;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final ObjectMapper mapper;

    private static final String SYSTEM = """
            You are Papyrus, an intelligent document assistant specialising in committee \
            meeting documents, resolutions, and records. \
            When relevant document excerpts are provided below, use them to answer accurately \
            and cite the source filename. If no excerpts are relevant, say so honestly. \
            Be concise and clear.

            ## Formatting
            Always use Markdown in your responses: use headings (##, ###) to organise sections, \
            bullet lists or numbered lists for items, **bold** for key terms, and fenced code \
            blocks for any structured data or verbatim text. \
            Tables are encouraged for comparative or structured information.

            ## PDF Export
            When the user asks for a printable version, a PDF, or says they want to download \
            or export the response, produce a well-structured, self-contained document using \
            Markdown. Include a clear title heading (# Title), organised sections with ## headings, \
            and a concise summary or conclusion at the end. \
            The response will be rendered into a formatted PDF automatically by the UI — \
            so prioritise clarity, logical structure, and completeness over brevity.\
            """;

    public ChatController(
            @Value("${papyrus.ocr.correction.api-key:}") String apiKey,
            EmbeddingService embeddingService,
            VectorStoreService vectorStoreService,
            ObjectMapper mapper) {
        this.embeddingService  = embeddingService;
        this.vectorStoreService = vectorStoreService;
        this.mapper = mapper;
        this.anthropic = (apiKey != null && !apiKey.isBlank())
                ? AnthropicOkHttpClient.builder().apiKey(apiKey).build()
                : AnthropicOkHttpClient.fromEnv();
    }

    record ChatMessage(String role, String content) {}
    record ChatRequest(List<ChatMessage> messages) {}

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

                String systemPrompt = SYSTEM;
                if (!userQuery.isBlank()) {
                    List<Float> vector = embeddingService.embed(userQuery);
                    var results = vectorStoreService.searchByVector(vector, 5, null);
                    if (!results.isEmpty()) {
                        var ctx = new StringBuilder("\n\n--- Relevant document excerpts ---\n\n");
                        for (var r : results) {
                            ctx.append("Source: ").append(r.sourceFilename()).append('\n');
                            ctx.append(r.chunk().content()).append("\n\n");
                        }
                        ctx.append("--- End of excerpts ---");
                        systemPrompt += ctx;
                    }
                }

                // ── Build conversation params ─────────────────────────────
                var builder = MessageCreateParams.builder()
                        .model("claude-opus-4-6")
                        .maxTokens(4096L)
                        .system(systemPrompt);

                for (var msg : request.messages()) {
                    if ("user".equals(msg.role()))           builder.addUserMessage(msg.content());
                    else if ("assistant".equals(msg.role())) builder.addAssistantMessage(msg.content());
                }

                // ── Stream response ───────────────────────────────────────
                try (StreamResponse<RawMessageStreamEvent> stream =
                             anthropic.messages().createStreaming(builder.build())) {

                    stream.stream()
                            .flatMap(e -> e.contentBlockDelta().stream())
                            .flatMap(e -> e.delta().text().stream())
                            .forEach(delta -> {
                                try {
                                    emitter.send(SseEmitter.event().data(
                                            mapper.writeValueAsString(delta.text())));
                                } catch (IOException ex) {
                                    throw new RuntimeException(ex);
                                }
                            });
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
