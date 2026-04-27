package io.darqlab.papyrus.pipeline.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.darqlab.papyrus.pipeline.config.PapyrusProperties;
import io.darqlab.papyrus.pipeline.config.PromptLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "papyrus.ocr.correction.provider", havingValue = "ollama")
public class OllamaOcrCorrectionService implements OcrCorrectionService {

    private static final Logger log = LoggerFactory.getLogger(OllamaOcrCorrectionService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_MODEL = "llava";

    private final boolean enabled;
    private final RestClient restClient;
    private final String model;
    private final String promptTemplate;

    public OllamaOcrCorrectionService(PapyrusProperties properties) {
        PapyrusProperties.CorrectionProperties cfg =
                properties.ocr() != null && properties.ocr().correction() != null
                        ? properties.ocr().correction()
                        : new PapyrusProperties.CorrectionProperties(false, null, null, null, null, null);

        PapyrusProperties.OllamaCorrectionProperties ollamaCfg = cfg.ollama();
        String baseUrl = ollamaCfg != null && ollamaCfg.baseUrl() != null
                ? ollamaCfg.baseUrl() : "http://localhost:11434";

        this.enabled = cfg.enabled();
        this.model   = cfg.model() != null ? cfg.model() : DEFAULT_MODEL;
        this.promptTemplate = PromptLoader.load("OCR_PROMPT_FILE", "prompts/ocr-correction.md");

        if (this.enabled) {
            this.restClient = RestClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader("Content-Type", "application/json")
                    .build();
            log.info("OllamaOcrCorrectionService active — baseUrl='{}', model='{}'", baseUrl, this.model);
        } else {
            this.restClient = null;
            log.info("OCR correction disabled");
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String correct(byte[] imageBytes, String mimeType, String rawOcrText) {
        if (!enabled) return rawOcrText;
        if (rawOcrText == null || rawOcrText.isBlank()) return rawOcrText;

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", promptTemplate.formatted(rawOcrText));
        message.put("images", List.of(Base64.getEncoder().encodeToString(imageBytes)));

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(message),
                "stream", false
        );

        try {
            String response = restClient.post()
                    .uri("/api/chat")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode node = MAPPER.readTree(response);
            String corrected = node.path("message").path("content").asText();
            if (corrected != null && !corrected.isBlank()) return corrected;
        } catch (Exception e) {
            log.warn("OCR correction failed, using raw Tesseract output: {}", e.getMessage());
        }

        return rawOcrText;
    }
}
