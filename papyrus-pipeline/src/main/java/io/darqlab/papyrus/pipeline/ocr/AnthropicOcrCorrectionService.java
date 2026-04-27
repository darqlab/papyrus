package io.darqlab.papyrus.pipeline.ocr;

import io.darqlab.papyrus.pipeline.config.PapyrusProperties;
import io.darqlab.papyrus.pipeline.config.PromptLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "papyrus.ocr.correction.provider", havingValue = "anthropic", matchIfMissing = true)
public class AnthropicOcrCorrectionService implements OcrCorrectionService {

    private static final Logger log = LoggerFactory.getLogger(AnthropicOcrCorrectionService.class);

    private static final String ANTHROPIC_API = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String DEFAULT_MODEL = "claude-sonnet-4-6";

    private final boolean enabled;
    private final RestClient restClient;
    private final String model;
    private final String promptTemplate;

    public AnthropicOcrCorrectionService(PapyrusProperties properties) {
        PapyrusProperties.CorrectionProperties cfg =
                properties.ocr() != null && properties.ocr().correction() != null
                        ? properties.ocr().correction()
                        : new PapyrusProperties.CorrectionProperties(false, null, null, null, null, null);

        PapyrusProperties.AnthropicCorrectionProperties anthropicCfg = cfg.anthropic();
        String apiKey = anthropicCfg != null ? anthropicCfg.apiKey() : null;

        this.enabled = cfg.enabled() && apiKey != null && !apiKey.isBlank();
        this.model   = cfg.model() != null ? cfg.model() : DEFAULT_MODEL;
        this.promptTemplate = PromptLoader.load("OCR_PROMPT_FILE", "prompts/ocr-correction.md");

        if (this.enabled) {
            this.restClient = RestClient.builder()
                    .baseUrl(ANTHROPIC_API)
                    .defaultHeader("x-api-key", apiKey)
                    .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
                    .defaultHeader("Content-Type", "application/json")
                    .build();
            log.info("AnthropicOcrCorrectionService active — model='{}'", this.model);
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
    @SuppressWarnings("unchecked")
    public String correct(byte[] imageBytes, String mimeType, String rawOcrText) {
        if (!enabled) return rawOcrText;
        if (rawOcrText == null || rawOcrText.isBlank()) return rawOcrText;

        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", 4096,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of(
                                        "type", "image",
                                        "source", Map.of(
                                                "type", "base64",
                                                "media_type", mimeType,
                                                "data", base64Image
                                        )
                                ),
                                Map.of(
                                        "type", "text",
                                        "text", promptTemplate.formatted(rawOcrText)
                                )
                        )
                ))
        );

        try {
            Map<String, Object> response = restClient.post()
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
            if (content != null && !content.isEmpty()) {
                return (String) content.get(0).get("text");
            }
        } catch (Exception e) {
            log.warn("OCR correction failed, using raw Tesseract output: {}", e.getMessage());
        }

        return rawOcrText;
    }
}
