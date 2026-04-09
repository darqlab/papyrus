package io.darqlab.papyrus.mcp.service;

import io.darqlab.papyrus.core.domain.ExtractedText;
import io.darqlab.papyrus.core.domain.IngestionStatus;
import io.darqlab.papyrus.core.service.EmbeddingService;
import io.darqlab.papyrus.core.util.MimeTypeDetector;
import io.darqlab.papyrus.core.util.TokenEstimator;
import io.darqlab.papyrus.extractor.FormatRouter;
import io.darqlab.papyrus.pipeline.chunking.ChunkingService;
import io.darqlab.papyrus.pipeline.store.VectorStoreService;
import io.darqlab.papyrus.pipeline.store.entity.DocumentSourceEntity;
import io.darqlab.papyrus.pipeline.store.repository.DocumentSourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

@Service
public class IngestionOrchestrator {

    private final FormatRouter formatRouter;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final DocumentSourceRepository sourceRepository;

    public IngestionOrchestrator(FormatRouter formatRouter,
                                 ChunkingService chunkingService,
                                 EmbeddingService embeddingService,
                                 VectorStoreService vectorStoreService,
                                 DocumentSourceRepository sourceRepository) {
        this.formatRouter       = formatRouter;
        this.chunkingService    = chunkingService;
        this.embeddingService   = embeddingService;
        this.vectorStoreService = vectorStoreService;
        this.sourceRepository   = sourceRepository;
    }

    @Transactional
    public IngestionResult ingest(byte[] content, String filename, String language) {
        String mimeType = MimeTypeDetector.detect(filename);
        UUID sourceId = UUID.randomUUID();

        DocumentSourceEntity source = new DocumentSourceEntity(
                sourceId, filename, mimeType, (long) content.length,
                language, IngestionStatus.PROCESSING);
        sourceRepository.save(source);

        try {
            ExtractedText extracted = formatRouter.route(
                    new ByteArrayInputStream(content), filename);

            List<String> chunks = chunkingService.chunk(extracted);

            source.setPageCount(extracted.pageCount());

            if (chunks.isEmpty()) {
                source.setStatus(IngestionStatus.DONE);
                sourceRepository.save(source);
                return new IngestionResult(sourceId, 0);
            }

            List<List<Float>> embeddings = chunks.stream()
                    .map(embeddingService::embed)
                    .toList();

            List<Integer> tokenCounts = chunks.stream()
                    .map(TokenEstimator::estimate)
                    .toList();

            vectorStoreService.storeChunks(sourceId, chunks, embeddings, tokenCounts, null);

            source.setStatus(IngestionStatus.DONE);
            sourceRepository.save(source);

            return new IngestionResult(sourceId, chunks.size());

        } catch (Exception e) {
            source.setStatus(IngestionStatus.FAILED);
            source.setError(e.getMessage());
            sourceRepository.save(source);
            throw new RuntimeException("Ingestion failed for " + filename + ": " + e.getMessage(), e);
        }
    }

    public record IngestionResult(UUID sourceId, int chunkCount) {}
}
