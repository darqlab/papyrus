package io.darqlab.papyrus.pipeline.chunking;

import io.darqlab.papyrus.core.domain.ExtractedText;
import io.darqlab.papyrus.core.service.EmbeddingService;
import io.darqlab.papyrus.core.util.TextNormalizer;
import io.darqlab.papyrus.core.util.TokenEstimator;
import io.darqlab.papyrus.pipeline.config.PapyrusProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChunkingService {

    private final PapyrusProperties properties;
    private final EmbeddingService embeddingService;

    public ChunkingService(PapyrusProperties properties, EmbeddingService embeddingService) {
        this.properties       = properties;
        this.embeddingService = embeddingService;
    }

    public List<String> chunk(ExtractedText extracted) {
        return chunk(extracted, globalConfig());
    }

    public List<String> chunk(ExtractedText extracted, ChunkingConfig config) {
        String normalized = TextNormalizer.normalize(extracted.content());
        if (normalized.isBlank()) return List.of();

        return switch (config.strategy()) {
            case PARAGRAPH -> chunkByParagraph(normalized, config);
            case PAGE      -> chunkByPage(extracted.pageTexts(), config);
            case FIXED     -> chunkFixed(normalized, config);
            case SEMANTIC  -> chunkBySemantic(normalized, config);
            case SECTION   -> chunkBySection(normalized, config);
        };
    }

    // ── Strategies ────────────────────────────────────────────────────────────

    private List<String> chunkByParagraph(String content, ChunkingConfig config) {
        List<String> paragraphs = Arrays.stream(content.split("\n\n+"))
                .map(String::strip)
                .filter(p -> !p.isBlank())
                .toList();

        List<String> chunks    = new ArrayList<>();
        List<String> current   = new ArrayList<>();
        int currentTokens      = 0;

        for (String paragraph : paragraphs) {
            int paraTokens = TokenEstimator.estimate(paragraph);

            if (currentTokens + paraTokens > config.maxTokens() && !current.isEmpty()) {
                chunks.add(String.join("\n\n", current));
                current       = overlap(current, config.overlapTokens());
                currentTokens = current.stream().mapToInt(TokenEstimator::estimate).sum();
            }

            current.add(paragraph);
            currentTokens += paraTokens;
        }

        if (!current.isEmpty()) chunks.add(String.join("\n\n", current));
        return chunks;
    }

    private List<String> chunkByPage(List<String> pageTexts, ChunkingConfig config) {
        List<String> chunks = new ArrayList<>();
        for (String page : pageTexts) {
            String normalized = TextNormalizer.normalize(page);
            if (normalized.isBlank()) continue;

            if (TokenEstimator.exceedsLimit(normalized, config.maxTokens())) {
                chunks.addAll(fixedSplit(normalized, config));
            } else {
                chunks.add(normalized);
            }
        }
        return chunks;
    }

    private List<String> chunkFixed(String content, ChunkingConfig config) {
        return fixedSplit(content, config);
    }

    private List<String> chunkBySemantic(String content, ChunkingConfig config) {
        float threshold  = properties.chunking().semantic().threshold();
        int minSentences = properties.chunking().semantic().minSentences();

        List<String> sentences = SentenceSplitter.split(content);
        if (sentences.size() <= minSentences) return List.of(content);

        List<List<Float>> embeddings = embeddingService.embedBatch(sentences);

        List<String> chunks  = new ArrayList<>();
        List<String> current = new ArrayList<>();

        for (int i = 0; i < sentences.size(); i++) {
            current.add(sentences.get(i));

            boolean atBreakpoint = i < sentences.size() - 1
                    && current.size() >= minSentences
                    && cosineSimilarity(embeddings.get(i), embeddings.get(i + 1)) < threshold;

            if (atBreakpoint) {
                String chunk = String.join(" ", current);
                chunks.addAll(TokenEstimator.exceedsLimit(chunk, config.maxTokens())
                        ? fixedSplit(chunk, config)
                        : List.of(chunk));
                current = new ArrayList<>();
            }
        }

        if (!current.isEmpty()) {
            String chunk = String.join(" ", current);
            chunks.addAll(TokenEstimator.exceedsLimit(chunk, config.maxTokens())
                    ? fixedSplit(chunk, config)
                    : List.of(chunk));
        }

        return chunks;
    }

    private List<String> chunkBySection(String content, ChunkingConfig config) {
        String patternStr = properties.chunking().section().pattern();
        int minTokens     = properties.chunking().section().minTokens();
        Pattern header    = Pattern.compile(patternStr, Pattern.MULTILINE);

        List<String> sections = new ArrayList<>();
        Matcher m = header.matcher(content);
        int lastStart = 0;
        while (m.find()) {
            if (m.start() > lastStart) {
                sections.add(content.substring(lastStart, m.start()).strip());
            }
            lastStart = m.start();
        }
        sections.add(content.substring(lastStart).strip());
        sections.removeIf(String::isBlank);

        List<String> chunks   = new ArrayList<>();
        StringBuilder pending = new StringBuilder();
        int pendingTokens     = 0;

        for (String section : sections) {
            int sectionTokens = TokenEstimator.estimate(section);

            if (sectionTokens > config.maxTokens()) {
                if (!pending.isEmpty()) {
                    chunks.add(pending.toString().strip());
                    pending.setLength(0);
                    pendingTokens = 0;
                }
                chunks.addAll(fixedSplit(section, config));
                continue;
            }

            if (pendingTokens + sectionTokens > config.maxTokens() && pendingTokens >= minTokens) {
                chunks.add(pending.toString().strip());
                pending.setLength(0);
                pendingTokens = 0;
            }

            if (!pending.isEmpty()) pending.append("\n\n");
            pending.append(section);
            pendingTokens += sectionTokens;
        }

        if (!pending.isEmpty()) chunks.add(pending.toString().strip());
        return chunks;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<String> fixedSplit(String content, ChunkingConfig config) {
        // 1 token ≈ 0.75 words
        int stepWords    = (int) (config.maxTokens() * 0.75);
        int overlapWords = (int) (config.overlapTokens() * 0.75);

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

    private ChunkingConfig globalConfig() {
        return new ChunkingConfig(
                properties.chunking().strategy(),
                properties.chunking().maxTokens(),
                properties.chunking().overlapTokens()
        );
    }

    private static float cosineSimilarity(List<Float> a, List<Float> b) {
        float dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.size(); i++) {
            dot   += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        return (normA == 0 || normB == 0) ? 0f
                : dot / (float) (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
