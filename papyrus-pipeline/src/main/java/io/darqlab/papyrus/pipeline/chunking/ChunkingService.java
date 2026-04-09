package io.darqlab.papyrus.pipeline.chunking;

import io.darqlab.papyrus.core.util.TextNormalizer;
import io.darqlab.papyrus.core.util.TokenEstimator;
import io.darqlab.papyrus.core.domain.ExtractedText;
import io.darqlab.papyrus.pipeline.config.PapyrusProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class ChunkingService {

    private final PapyrusProperties properties;

    public ChunkingService(PapyrusProperties properties) {
        this.properties = properties;
    }

    public List<String> chunk(ExtractedText extracted) {
        String normalized = TextNormalizer.normalize(extracted.content());
        if (normalized.isBlank()) {
            return List.of();
        }

        return switch (properties.chunking().strategy()) {
            case PARAGRAPH -> chunkByParagraph(normalized);
            case PAGE      -> chunkByPage(extracted.pageTexts());
            case FIXED     -> chunkFixed(normalized);
        };
    }

    // ── Strategies ────────────────────────────────────────────────────────────

    private List<String> chunkByParagraph(String content) {
        int maxTokens     = properties.chunking().maxTokens();
        int overlapTokens = properties.chunking().overlapTokens();

        List<String> paragraphs = Arrays.stream(content.split("\n\n+"))
                .map(String::strip)
                .filter(p -> !p.isBlank())
                .toList();

        List<String> chunks     = new ArrayList<>();
        List<String> current    = new ArrayList<>();
        int currentTokens       = 0;

        for (String paragraph : paragraphs) {
            int paraTokens = TokenEstimator.estimate(paragraph);

            if (currentTokens + paraTokens > maxTokens && !current.isEmpty()) {
                chunks.add(String.join("\n\n", current));
                current   = overlap(current, overlapTokens);
                currentTokens = current.stream().mapToInt(TokenEstimator::estimate).sum();
            }

            current.add(paragraph);
            currentTokens += paraTokens;
        }

        if (!current.isEmpty()) {
            chunks.add(String.join("\n\n", current));
        }

        return chunks;
    }

    private List<String> chunkByPage(List<String> pageTexts) {
        int maxTokens = properties.chunking().maxTokens();

        List<String> chunks = new ArrayList<>();
        for (String page : pageTexts) {
            String normalized = TextNormalizer.normalize(page);
            if (normalized.isBlank()) continue;

            if (TokenEstimator.exceedsLimit(normalized, maxTokens)) {
                // Page is too long — split it with fixed strategy
                chunks.addAll(fixedSplit(normalized));
            } else {
                chunks.add(normalized);
            }
        }
        return chunks;
    }

    private List<String> chunkFixed(String content) {
        return fixedSplit(content);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<String> fixedSplit(String content) {
        int maxTokens     = properties.chunking().maxTokens();
        int overlapTokens = properties.chunking().overlapTokens();
        // Approx: 1 token ≈ 4 chars; convert to word count (1 token ≈ 0.75 words)
        int stepWords     = (int) (maxTokens * 0.75);
        int overlapWords  = (int) (overlapTokens * 0.75);

        String[] words = content.trim().split("\\s+");
        if (words.length == 0) return List.of();

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < words.length) {
            int end = Math.min(start + stepWords, words.length);
            chunks.add(String.join(" ", Arrays.copyOfRange(words, start, end)));
            start += stepWords - overlapWords;
            if (start >= words.length || stepWords <= overlapWords) break;
        }
        return chunks;
    }

    /** Return the trailing paragraphs from {@code paragraphs} that together fit within {@code overlapTokens}. */
    private List<String> overlap(List<String> paragraphs, int overlapTokens) {
        List<String> result = new ArrayList<>();
        int tokens = 0;
        for (int i = paragraphs.size() - 1; i >= 0; i--) {
            int t = TokenEstimator.estimate(paragraphs.get(i));
            if (tokens + t > overlapTokens) break;
            result.add(0, paragraphs.get(i));
            tokens += t;
        }
        return result;
    }
}
