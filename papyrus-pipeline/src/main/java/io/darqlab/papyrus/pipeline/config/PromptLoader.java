package io.darqlab.papyrus.pipeline.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads a system prompt from either an external file (env var override) or a
 * classpath resource (default shipped with the JAR).
 *
 * <p>Loading order:
 * <ol>
 *   <li>If {@code envVarName} is set in the environment and the path exists → load from disk.
 *   <li>Otherwise → load from {@code classpathResource}.
 * </ol>
 *
 * <p>Fails fast at startup if the resolved prompt is blank or fewer than 50 characters.
 */
public final class PromptLoader {

    private static final Logger log = LoggerFactory.getLogger(PromptLoader.class);
    private static final int MIN_LENGTH = 50;

    private PromptLoader() {}

    /**
     * Load a prompt, failing fast on missing or too-short content.
     *
     * @param envVarName       environment variable name that may hold an external file path
     * @param classpathResource classpath-relative path to the default prompt file (e.g. {@code "prompts/chat-system.md"})
     * @return the prompt text, never blank
     * @throws IllegalStateException if the prompt cannot be loaded or is too short
     */
    public static String load(String envVarName, String classpathResource) {
        String externalPath = System.getenv(envVarName);

        if (externalPath != null && !externalPath.isBlank()) {
            Path path = Path.of(externalPath);
            if (!Files.exists(path)) {
                throw new IllegalStateException(
                        "Prompt file specified by %s does not exist: %s".formatted(envVarName, externalPath));
            }
            try {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                validate(content, "external file " + externalPath);
                log.info("Loaded prompt from external file [{}={}]", envVarName, externalPath);
                return content;
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Failed to read prompt file %s: %s".formatted(externalPath, e.getMessage()), e);
            }
        }

        try {
            ClassPathResource resource = new ClassPathResource(classpathResource);
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            validate(content, "classpath:" + classpathResource);
            log.info("Loaded prompt from classpath [{}]", classpathResource);
            return content;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read classpath prompt resource %s: %s".formatted(classpathResource, e.getMessage()), e);
        }
    }

    private static void validate(String content, String source) {
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Prompt loaded from %s is blank".formatted(source));
        }
        if (content.strip().length() < MIN_LENGTH) {
            throw new IllegalStateException(
                    "Prompt loaded from %s is too short (%d chars, minimum %d)"
                            .formatted(source, content.strip().length(), MIN_LENGTH));
        }
    }
}
