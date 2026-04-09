package io.darqlab.papyrus.pipeline.ocr;

import io.darqlab.papyrus.pipeline.config.PapyrusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Post-processes raw Tesseract OCR output using Claude's vision API.
 *
 * <p>Sends the original image (as base64) alongside the raw OCR text so Claude
 * can see the actual content and correct recognition errors caused by blur,
 * skew, unusual fonts, or low contrast.
 *
 * <p>Enabled via {@code papyrus.ocr.correction.enabled=true} and requires
 * {@code papyrus.ocr.correction.api-key} (Anthropic API key).
 */
@Service
public class OcrCorrectionService {

    private static final Logger log = LoggerFactory.getLogger(OcrCorrectionService.class);

    private static final String ANTHROPIC_API = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String DEFAULT_MODEL = "claude-sonnet-4-6";

    private static final String PROMPT = """
            The text below was extracted from the attached image using Tesseract OCR. \
            The image may be blurry, skewed, or low-contrast, causing recognition errors.

            Please carefully read the image and return the fully corrected, clean text. \
            Rules:
            - Fix misspelled or misread words (e.g. "COMMITIEE" → "COMMITTEE", "Hegq" → correct word)
            - Fix spacing and word-boundary errors caused by poor OCR
            - Remove stray/extra characters, random symbols, and noise that are not part of the real content
            - Remove repeated characters or garbled fragments that don't belong
            - Preserve the original structure (line breaks, paragraphs, lists, numbers)
            - Do NOT add, rephrase, or summarize content — only correct and clean OCR output
            - Return only the corrected text, nothing else

            Raw OCR output:
            %s
            """;

    private final boolean enabled;
    private final RestClient restClient;
    private final String model;

    public OcrCorrectionService(PapyrusProperties properties) {
        PapyrusProperties.CorrectionProperties cfg =
                properties.ocr() != null && properties.ocr().correction() != null
                        ? properties.ocr().correction()
                        : new PapyrusProperties.CorrectionProperties(false, null, null);

        this.enabled = cfg.enabled() && cfg.apiKey() != null && !cfg.apiKey().isBlank();
        this.model   = cfg.model() != null ? cfg.model() : DEFAULT_MODEL;

        if (this.enabled) {
            this.restClient = RestClient.builder()
                    .baseUrl(ANTHROPIC_API)
                    .defaultHeader("x-api-key", cfg.apiKey())
                    .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
                    .defaultHeader("Content-Type", "application/json")
                    .build();
            log.info("OCR correction enabled using model '{}'", this.model);
        } else {
            this.restClient = null;
            log.info("OCR correction disabled");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Correct raw OCR text using Claude vision.
     *
     * @param imageBytes  the original image bytes
     * @param mimeType    image MIME type (e.g. "image/png")
     * @param rawOcrText  text produced by Tesseract
     * @return corrected text, or the original {@code rawOcrText} if correction fails
     */
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
                                        "text", PROMPT.formatted(rawOcrText)
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
