package io.darqlab.papyrus.api.service;

import io.darqlab.papyrus.core.domain.ExtractedText;
import io.darqlab.papyrus.core.domain.IngestionStatus;
import io.darqlab.papyrus.core.service.EmbeddingService;
import io.darqlab.papyrus.core.util.MimeTypeDetector;
import io.darqlab.papyrus.core.util.TokenEstimator;
import io.darqlab.papyrus.extractor.FormatRouter;
import io.darqlab.papyrus.pipeline.chunking.ChunkingService;
import io.darqlab.papyrus.pipeline.ocr.OcrCorrectionService;
import io.darqlab.papyrus.pipeline.store.VectorStoreService;
import io.darqlab.papyrus.pipeline.store.entity.DocumentSourceEntity;
import io.darqlab.papyrus.pipeline.store.repository.DocumentSourceRepository;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Set<String> IMAGE_MIME_TYPES = Set.of(
            "image/png", "image/jpeg", "image/tiff", "image/bmp", "image/gif");

    private final FormatRouter formatRouter;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final DocumentSourceRepository sourceRepository;
    private final OcrCorrectionService ocrCorrectionService;

    public DocumentService(FormatRouter formatRouter,
                           ChunkingService chunkingService,
                           EmbeddingService embeddingService,
                           VectorStoreService vectorStoreService,
                           DocumentSourceRepository sourceRepository,
                           OcrCorrectionService ocrCorrectionService) {
        this.formatRouter          = formatRouter;
        this.chunkingService       = chunkingService;
        this.embeddingService      = embeddingService;
        this.vectorStoreService    = vectorStoreService;
        this.sourceRepository      = sourceRepository;
        this.ocrCorrectionService  = ocrCorrectionService;
    }

    @Transactional
    public IngestionResult ingest(byte[] content, String filename, String language) {
        return ingest(content, filename, language, null);
    }

    @Transactional
    public IngestionResult ingest(byte[] content, String filename, String language, String preExtractedText) {
        String mimeType = MimeTypeDetector.detect(filename);
        UUID sourceId   = UUID.randomUUID();

        DocumentSourceEntity source = new DocumentSourceEntity(
                sourceId, filename, mimeType, (long) content.length,
                language, IngestionStatus.PROCESSING);
        sourceRepository.saveAndFlush(source);
        try {
            ExtractedText extracted;
            if (preExtractedText != null && !preExtractedText.isBlank()) {
                // Use caller-supplied text (e.g. user-edited OCR output)
                extracted = ExtractedText.of(preExtractedText);
            } else {
                extracted = formatRouter.route(new ByteArrayInputStream(content), filename);
                // For image files: apply LLM correction on top of raw Tesseract output
                if (IMAGE_MIME_TYPES.contains(mimeType) && ocrCorrectionService.isEnabled()) {
                    String corrected = ocrCorrectionService.correct(content, mimeType, extracted.content());
                    extracted = ExtractedText.of(corrected);
                }
            }

            List<String> chunks = chunkingService.chunk(extracted);

            source.setPageCount(extracted.pageCount());

            if (chunks.isEmpty()) {
                source.setStatus(IngestionStatus.DONE);
                sourceRepository.save(source);
                return new IngestionResult(sourceId, filename, 0);
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

            return new IngestionResult(sourceId, filename, chunks.size());

        } catch (Exception e) {
            source.setStatus(IngestionStatus.FAILED);
            source.setError(e.getMessage());
            sourceRepository.save(source);
            throw new RuntimeException("Ingestion failed for " + filename, e);
        }
    }

    /**
     * Fetch a URL with Jsoup, then ingest the HTML content.
     */
    @Transactional
    public IngestionResult ingestUrl(String url, String language) {
        try {
            byte[] html = Jsoup.connect(url)
                    .userAgent("Papyrus/0.1 (+https://github.com/darqlab/papyrus)")
                    .timeout(15_000)
                    .execute()
                    .bodyAsBytes();

            String filename = URI.create(url).getHost() + ".html";
            return ingest(html, filename, language);

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch URL: " + url + " — " + e.getMessage(), e);
        }
    }

    /**
     * Delete all chunks and the source record for the given source ID.
     * Returns false if the source does not exist.
     */
    @Transactional
    public boolean delete(UUID sourceId) {
        if (!sourceRepository.existsById(sourceId)) {
            return false;
        }
        vectorStoreService.deleteBySourceId(sourceId);
        return true;
    }

    public record IngestionResult(UUID sourceId, String filename, int chunkCount) {}

    /**
     * Extract text from a file without storing anything — used for OCR preview/verification.
     */
    public String preview(byte[] content, String filename) {
        String mimeType = MimeTypeDetector.detect(filename);
        ExtractedText extracted = formatRouter.route(new ByteArrayInputStream(content), filename);

        if (IMAGE_MIME_TYPES.contains(mimeType) && ocrCorrectionService.isEnabled()) {
            return ocrCorrectionService.correct(content, mimeType, extracted.content());
        }
        return extracted.content();
    }
}
