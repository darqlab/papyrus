package io.darqlab.papyrus.pipeline.chat;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.RawMessageStreamEvent;
import io.darqlab.papyrus.core.domain.ChatTurn;
import io.darqlab.papyrus.core.service.ChatService;
import io.darqlab.papyrus.pipeline.config.PapyrusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Stream;

@Service
@ConditionalOnProperty(name = "papyrus.chat.provider", havingValue = "anthropic", matchIfMissing = true)
public class AnthropicChatService implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(AnthropicChatService.class);
    private static final long MAX_TOKENS = 4096L;

    private final AnthropicClient client;
    private final String model;

    public AnthropicChatService(PapyrusProperties properties) {
        PapyrusProperties.AnthropicChatProperties cfg = properties.chat().anthropic();
        this.model = cfg.model();
        this.client = (cfg.apiKey() != null && !cfg.apiKey().isBlank())
                ? AnthropicOkHttpClient.builder().apiKey(cfg.apiKey()).build()
                : AnthropicOkHttpClient.fromEnv();
        log.info("AnthropicChatService active — model '{}'", this.model);
    }

    @Override
    public Stream<String> streamChat(List<ChatTurn> history, String systemPrompt) {
        var builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .system(systemPrompt);

        for (ChatTurn turn : history) {
            if ("user".equals(turn.role()))           builder.addUserMessage(turn.content());
            else if ("assistant".equals(turn.role())) builder.addAssistantMessage(turn.content());
        }

        StreamResponse<RawMessageStreamEvent> stream =
                client.messages().createStreaming(builder.build());

        return stream.stream()
                .flatMap(event -> event.contentBlockDelta()
                        .flatMap(delta -> delta.delta().text())
                        .map(text -> Stream.of(text.text()))
                        .orElse(Stream.empty()))
                .onClose(stream::close);
    }
}
