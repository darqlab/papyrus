package io.darqlab.papyrus.pipeline.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.darqlab.papyrus.core.domain.ChatTurn;
import io.darqlab.papyrus.core.service.ChatService;
import io.darqlab.papyrus.pipeline.config.PapyrusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Service
@ConditionalOnProperty(name = "papyrus.chat.provider", havingValue = "ollama")
public class OllamaChatService implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(OllamaChatService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final String model;

    public OllamaChatService(PapyrusProperties properties) {
        PapyrusProperties.OllamaChatProperties cfg = properties.chat().ollama();
        this.model = cfg.model();
        this.restClient = RestClient.builder()
                .baseUrl(cfg.baseUrl())
                .defaultHeader("Content-Type", "application/json")
                .build();
        log.info("OllamaChatService active — baseUrl='{}', model='{}'", cfg.baseUrl(), this.model);
    }

    @Override
    public Stream<String> streamChat(List<ChatTurn> history, String systemPrompt) {
        List<Map<String, String>> messages = new java.util.ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        for (ChatTurn turn : history) {
            messages.add(Map.of("role", turn.role(), "content", turn.content()));
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "stream", true
        );

        InputStream responseStream = restClient.post()
                .uri("/api/chat")
                .body(body)
                .retrieve()
                .body(InputStream.class);

        if (responseStream == null) {
            return Stream.empty();
        }

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(responseStream, StandardCharsets.UTF_8));

        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(reader.lines().iterator(), Spliterator.ORDERED),
                false
        ).flatMap(line -> {
            if (line.isBlank()) return Stream.empty();
            try {
                JsonNode node = MAPPER.readTree(line);
                JsonNode content = node.path("message").path("content");
                if (!content.isMissingNode() && !content.asText().isEmpty()) {
                    return Stream.of(content.asText());
                }
            } catch (Exception e) {
                log.debug("Skipping unparseable NDJSON line: {}", line);
            }
            return Stream.empty();
        }).onClose(() -> {
            try { responseStream.close(); } catch (Exception ignored) {}
        });
    }
}
