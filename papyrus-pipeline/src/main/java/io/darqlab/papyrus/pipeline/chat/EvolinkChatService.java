package io.darqlab.papyrus.pipeline.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.darqlab.papyrus.core.domain.ChatTurn;
import io.darqlab.papyrus.core.exception.CreditExhaustedException;
import io.darqlab.papyrus.core.service.ChatService;
import io.darqlab.papyrus.pipeline.config.PapyrusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Service
@ConditionalOnProperty(name = "papyrus.chat.provider", havingValue = "evolink")
public class EvolinkChatService implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(EvolinkChatService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final String model;

    public EvolinkChatService(PapyrusProperties properties) {
        PapyrusProperties.EvolinkChatProperties cfg = properties.chat().evolink();
        this.model = cfg.model();
        this.restClient = RestClient.builder()
                .baseUrl(cfg.baseUrl())
                .defaultHeader("Authorization", "Bearer " + cfg.apiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
        log.info("EvolinkChatService active — baseUrl='{}', model='{}'", cfg.baseUrl(), this.model);
    }

    @Override
    public Stream<String> streamChat(List<ChatTurn> history, String systemPrompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        for (ChatTurn turn : history) {
            messages.add(Map.of("role", turn.role(), "content", turn.content()));
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "stream", true
        );

        InputStream responseStream;
        try {
            responseStream = restClient.post()
                    .uri("/v1/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(InputStream.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 402 || status == 429) {
                throw new CreditExhaustedException("Evolink quota exhausted (HTTP " + status + ")", e);
            }
            throw e;
        }

        if (responseStream == null) return Stream.empty();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(responseStream, StandardCharsets.UTF_8));

        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(reader.lines().iterator(), Spliterator.ORDERED),
                false
        ).flatMap(line -> parseSseLine(line).map(Stream::of).orElse(Stream.empty()))
        .onClose(() -> {
            try { responseStream.close(); } catch (Exception ignored) {}
        });
    }

    Optional<String> parseSseLine(String line) {
        if (line.isBlank() || !line.startsWith("data: ")) return Optional.empty();
        String payload = line.substring(6).trim();
        if ("[DONE]".equals(payload)) return Optional.empty();
        try {
            JsonNode node = MAPPER.readTree(payload);
            JsonNode content = node.path("choices").path(0).path("delta").path("content");
            if (!content.isMissingNode() && !content.isNull() && !content.asText().isEmpty()) {
                return Optional.of(content.asText());
            }
        } catch (Exception e) {
            log.debug("Skipping unparseable SSE line: {}", line);
        }
        return Optional.empty();
    }
}
