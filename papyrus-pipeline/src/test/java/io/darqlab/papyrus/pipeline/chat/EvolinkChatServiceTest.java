package io.darqlab.papyrus.pipeline.chat;

import io.darqlab.papyrus.pipeline.config.ChunkingStrategy;
import io.darqlab.papyrus.pipeline.config.PapyrusProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EvolinkChatServiceTest {

    private EvolinkChatService service;

    @BeforeEach
    void setUp() {
        PapyrusProperties props = new PapyrusProperties(
                new PapyrusProperties.EmbeddingProperties("voyage",
                        new PapyrusProperties.VoyageProperties("key", "voyage-3-lite"),
                        new PapyrusProperties.OllamaProperties("http://localhost:11434", "nomic-embed-text")),
                new PapyrusProperties.ChunkingProperties(ChunkingStrategy.PARAGRAPH, 512, 64),
                new PapyrusProperties.SearchProperties(5),
                new PapyrusProperties.OcrProperties(
                        new PapyrusProperties.CorrectionProperties(false, null, null, null, null, null)),
                new PapyrusProperties.ArchiveProperties(false, null, null),
                new PapyrusProperties.ChatProperties(
                        "evolink", null, null,
                        new PapyrusProperties.EvolinkChatProperties("sk-evo-test", "https://direct.evolink.ai", "evolink/auto"))
        );
        service = new EvolinkChatService(props);
    }

    @Test
    void parsesTokenFromSseLine() {
        String line = "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"},\"finish_reason\":null}]}";
        Optional<String> result = service.parseSseLine(line);
        assertTrue(result.isPresent());
        assertEquals("Hello", result.get());
    }

    @Test
    void ignoresDoneMarker() {
        Optional<String> result = service.parseSseLine("data: [DONE]");
        assertFalse(result.isPresent());
    }

    @Test
    void ignoresBlankLine() {
        assertFalse(service.parseSseLine("").isPresent());
        assertFalse(service.parseSseLine("   ").isPresent());
    }

    @Test
    void ignoresNonDataLine() {
        assertFalse(service.parseSseLine("event: ping").isPresent());
        assertFalse(service.parseSseLine(": keep-alive").isPresent());
    }

    @Test
    void ignoresMalformedJson() {
        Optional<String> result = service.parseSseLine("data: {not valid json}");
        assertFalse(result.isPresent());
    }

    @Test
    void ignoresEmptyDeltaContent() {
        String line = "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"},\"finish_reason\":null}]}";
        assertFalse(service.parseSseLine(line).isPresent());
    }

    @Test
    void ignoresNullDeltaContent() {
        String line = "data: {\"choices\":[{\"delta\":{\"content\":null},\"finish_reason\":\"stop\"}]}";
        assertFalse(service.parseSseLine(line).isPresent());
    }
}
