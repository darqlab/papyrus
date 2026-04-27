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
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "papyrus.ocr.correction.provider", havingValue = "evolink")
public class EvolinkOcrCorrectionService implements OcrCorrectionService {

    private static final Logger log = LoggerFactory.getLogger(EvolinkOcrCorrectionService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_BASE_URL = "https://direct.evolink.ai";
    private static final String DEFAULT_MODEL = "evolink/auto";

    private final boolean enabled;
    private final RestClient restClient;
    private final String model;
    private final String promptTemplate;

    public EvolinkOcrCorrectionService(PapyrusProperties properties) {
        PapyrusProperties.CorrectionProperties cfg =
                properties.ocr() != null && properties.ocr().correction() != null
                        ? properties.ocr().correction()
                        : new PapyrusProperties.CorrectionProperties(false, null, null, null, null, null);

        PapyrusProperties.EvolinkCorrectionProperties evolinkCfg = cfg.evolink();
        String apiKey = evolinkCfg != null ? evolinkCfg.apiKey() : null;

        this.enabled = cfg.enabled() && apiKey != null && !apiKey.isBlank();
        this.model   = cfg.model() != null ? cfg.model() : DEFAULT_MODEL;
        this.promptTemplate = PromptLoader.load("OCR_PROMPT_FILE", "prompts/ocr-correction.md");

        if (this.enabled) {
            this.restClient = RestClient.builder()
                    .baseUrl(DEFAULT_BASE_URL)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .defaultHeader("Content-Type", "application/json")
                    .build();
            log.info("EvolinkOcrCorrectionService active — model='{}'", this.model);
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

        String dataUri = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);

        List<Map<String, Object>> content = List.of(
                Map.of("type", "image_url", "image_url", Map.of("url", dataUri)),
                Map.of("type", "text", "text", promptTemplate.formatted(rawOcrText))
        );

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", content))
        );

        try {
            String response = restClient.post()
                    .uri("/v1/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode node = MAPPER.readTree(response);
            String corrected = node.path("choices").path(0).path("message").path("content").asText();
            if (corrected != null && !corrected.isBlank()) return corrected;
        } catch (Exception e) {
            log.warn("OCR correction failed, using raw Tesseract output: {}", e.getMessage());
        }

        return rawOcrText;
    }
}
