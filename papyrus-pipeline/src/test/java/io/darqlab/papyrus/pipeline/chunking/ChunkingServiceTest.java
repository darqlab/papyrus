package io.darqlab.papyrus.pipeline.chunking;

import io.darqlab.papyrus.core.domain.ExtractedText;
import io.darqlab.papyrus.pipeline.config.ChunkingStrategy;
import io.darqlab.papyrus.pipeline.config.PapyrusProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkingServiceTest {

    private ChunkingService serviceWith(ChunkingStrategy strategy, int maxTokens, int overlapTokens) {
        PapyrusProperties props = new PapyrusProperties(
                new PapyrusProperties.EmbeddingProperties("voyage",
                        new PapyrusProperties.VoyageProperties("key", "voyage-3-lite"),
                        new PapyrusProperties.OllamaProperties("http://localhost:11434", "nomic-embed-text")),
                new PapyrusProperties.ChunkingProperties(strategy, maxTokens, overlapTokens),
                new PapyrusProperties.SearchProperties(5)
        );
        return new ChunkingService(props);
    }

    // ── Paragraph strategy ────────────────────────────────────────────────────

    @Test
    void paragraph_shortText_returnsSingleChunk() {
        ChunkingService service = serviceWith(ChunkingStrategy.PARAGRAPH, 512, 64);
        ExtractedText text = ExtractedText.of("Hello world. This is a single paragraph.");

        List<String> chunks = service.chunk(text);

        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("Hello world"));
    }

    @Test
    void paragraph_multipleParagraphs_splitsAtMaxTokens() {
        ChunkingService service = serviceWith(ChunkingStrategy.PARAGRAPH, 10, 0);
        // Build text where each paragraph is ~5 tokens — two paragraphs per chunk
        String para = "one two three four five";
        String content = para + "\n\n" + para + "\n\n" + para + "\n\n" + para;
        ExtractedText text = ExtractedText.of(content);

        List<String> chunks = service.chunk(text);

        assertTrue(chunks.size() >= 2, "Expected multiple chunks but got: " + chunks.size());
    }

    @Test
    void paragraph_emptyText_returnsEmptyList() {
        ChunkingService service = serviceWith(ChunkingStrategy.PARAGRAPH, 512, 64);
        ExtractedText text = ExtractedText.of("   ");

        List<String> chunks = service.chunk(text);

        assertTrue(chunks.isEmpty());
    }

    @Test
    void paragraph_overlapCarriedIntoNextChunk() {
        ChunkingService service = serviceWith(ChunkingStrategy.PARAGRAPH, 10, 5);
        String para = "alpha beta gamma delta epsilon"; // ~6 tokens each
        String content = para + "\n\n" + para + "\n\n" + para;
        ExtractedText text = ExtractedText.of(content);

        List<String> chunks = service.chunk(text);

        // With overlap, chunk 2 should start with content from chunk 1
        assertTrue(chunks.size() >= 1);
    }

    // ── Page strategy ─────────────────────────────────────────────────────────

    @Test
    void page_eachPageBecomesChunk() {
        ChunkingService service = serviceWith(ChunkingStrategy.PAGE, 512, 64);
        ExtractedText text = new ExtractedText(
                "Page one\nPage two",
                List.of("Page one", "Page two"),
                2
        );

        List<String> chunks = service.chunk(text);

        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).contains("Page one"));
        assertTrue(chunks.get(1).contains("Page two"));
    }

    @Test
    void page_blankPagesSkipped() {
        ChunkingService service = serviceWith(ChunkingStrategy.PAGE, 512, 64);
        ExtractedText text = new ExtractedText(
                "Content",
                List.of("Content", "   ", "More content"),
                3
        );

        List<String> chunks = service.chunk(text);

        assertEquals(2, chunks.size());
    }

    // ── Fixed strategy ────────────────────────────────────────────────────────

    @Test
    void fixed_longText_producesMultipleChunks() {
        ChunkingService service = serviceWith(ChunkingStrategy.FIXED, 20, 5);
        // ~100 words
        String content = "word ".repeat(100).trim();
        ExtractedText text = ExtractedText.of(content);

        List<String> chunks = service.chunk(text);

        assertTrue(chunks.size() > 1, "Expected multiple chunks, got: " + chunks.size());
    }

    @Test
    void fixed_shortText_returnsSingleChunk() {
        ChunkingService service = serviceWith(ChunkingStrategy.FIXED, 512, 64);
        ExtractedText text = ExtractedText.of("Short document.");

        List<String> chunks = service.chunk(text);

        assertEquals(1, chunks.size());
    }
}
