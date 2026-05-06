package io.darqlab.papyrus.api.service;

import io.darqlab.papyrus.core.domain.ExtractedText;
import io.darqlab.papyrus.core.domain.IngestionStatus;
import io.darqlab.papyrus.core.domain.Source;
import io.darqlab.papyrus.core.service.EmbeddingService;
import io.darqlab.papyrus.core.util.MimeTypeDetector;
import io.darqlab.papyrus.core.util.TokenEstimator;
import io.darqlab.papyrus.extractor.FormatRouter;
import io.darqlab.papyrus.pipeline.archive.ArchiveService;
import io.darqlab.papyrus.pipeline.chunking.ChunkingConfig;
import io.darqlab.papyrus.pipeline.chunking.ChunkingService;
import io.darqlab.papyrus.pipeline.config.ChunkingStrategy;
import io.darqlab.papyrus.pipeline.config.PapyrusProperties;
import io.darqlab.papyrus.pipeline.ocr.OcrCorrectionService;
import io.darqlab.papyrus.pipeline.store.VectorStoreService;
import io.darqlab.papyrus.pipeline.store.entity.DocumentSourceEntity;
import io.darqlab.papyrus.pipeline.store.repository.DocumentSourceRepository;
import io.darqlab.papyrus.pipeline.util.ContentHasher;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.List;
import java.util.Optional;
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
    private final ArchiveService archiveService;
    private final PapyrusProperties properties;

    public DocumentService(FormatRouter formatRouter,
                           ChunkingService chunkingService,
                           EmbeddingService embeddingService,
                           VectorStoreService vectorStoreService,
                           DocumentSourceRepository sourceRepository,
                           OcrCorrectionService ocrCorrectionService,
                           ArchiveService archiveService,
                           PapyrusProperties properties) {
        this.formatRouter          = formatRouter;
        this.chunkingService       = chunkingService;
        this.embeddingService      = embeddingService;
        this.vectorStoreService    = vectorStoreService;
        this.sourceRepository      = sourceRepository;
        this.ocrCorrectionService  = ocrCorrectionService;
        this.archiveService        = archiveService;
        this.properties            = properties;
    }

    @Transactional
    public IngestionResult ingest(byte[] content, String filename, String language) {
        return ingest(content, filename, language, null, null, null, null, null);
    }

    @Transactional
    public IngestionResult ingest(byte[] content, String filename, String language, String preExtractedText) {
        return ingest(content, filename, language, preExtractedText, null, null, null, null);
    }

    @Transactional
    public IngestionResult ingest(byte[] content, String filename, String language, String preExtractedText, String archiveFilename) {
        return ingest(content, filename, language, preExtractedText, archiveFilename, null, null, null);
    }

    @Transactional
    public IngestionResult ingest(byte[] content, String filename, String language,
                                   String preExtractedText, String archiveFilename,
                                   ChunkingStrategy chunkingStrategy,
                                   Integer chunkingMaxTokens,
                                   Integer chunkingOverlapTokens) {
        String hash = ContentHasher.sha256(content);
        Optional<DocumentSourceEntity> existing = sourceRepository.findByContentHash(hash);
        if (existing.isPresent()) {
            DocumentSourceEntity e = existing.get();
            return new IngestionResult(e.getId(), e.getFilename(), 0, true);
        }

        String mimeType = MimeTypeDetector.detect(filename);
        UUID sourceId   = UUID.randomUUID();

        DocumentSourceEntity source = new DocumentSourceEntity(
                sourceId, filename, mimeType, (long) content.length,
                language, IngestionStatus.PROCESSING);
        source.setContentHash(hash);
        source.setChunkingStrategy(chunkingStrategy);
        source.setChunkingMaxTokens(chunkingMaxTokens);
        source.setChunkingOverlapTokens(chunkingOverlapTokens);
        sourceRepository.saveAndFlush(source);
        try {
            ExtractedText extracted;
            if (preExtractedText != null && !preExtractedText.isBlank()) {
                // User-edited OCR output — still counts as OCR for archiving
                boolean isOcrSource = IMAGE_MIME_TYPES.contains(mimeType);
                extracted = isOcrSource ? ExtractedText.ofOcr(preExtractedText) : ExtractedText.of(preExtractedText);
            } else {
                extracted = formatRouter.route(new ByteArrayInputStream(content), filename);
                // For image files: apply LLM correction on top of raw Tesseract output
                if (IMAGE_MIME_TYPES.contains(mimeType) && ocrCorrectionService.isEnabled()) {
                    String corrected = ocrCorrectionService.correct(content, mimeType, extracted.content());
                    extracted = ExtractedText.of(corrected);
                }
            }

            // Archive all file types — original + extracted text are the source of truth.
            // OCR files use content-derived naming; non-OCR files retain their original filename stem.
            boolean isOcr = IMAGE_MIME_TYPES.contains(mimeType);
            String effectiveArchiveName = archiveFilename; // user-supplied (OCR preview flow)
            if (!isOcr && (effectiveArchiveName == null || effectiveArchiveName.isBlank())) {
                effectiveArchiveName = stripExtension(filename);
            }
            String archivedAs = archiveService.archive(sourceId, filename, content, extracted.content(), effectiveArchiveName);
            if (archivedAs != null) {
                source.setArchiveFilename(archivedAs);
            }

            ChunkingConfig config = resolveConfig(chunkingStrategy, chunkingMaxTokens, chunkingOverlapTokens);
            List<String> chunks = chunkingService.chunk(extracted, config);

            source.setPageCount(extracted.pageCount());

            if (chunks.isEmpty()) {
                source.setStatus(IngestionStatus.DONE);
                sourceRepository.save(source);
                return new IngestionResult(sourceId, filename, 0, false);
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

            return new IngestionResult(sourceId, filename, chunks.size(), false);

        } catch (Exception e) {
            source.setStatus(IngestionStatus.FAILED);
            source.setError(e.getMessage());
            sourceRepository.save(source);
            throw new RuntimeException("Ingestion failed for " + filename, e);
        }
    }

    /**
     * Fetch a URL with Jsoup, then ingest the HTML content.
     * Deduplicates by URL first (no HTTP fetch needed), then by content hash after fetching.
     * After a new ingest, persists the source URL on the created entity.
     */
    @Transactional
    public IngestionResult ingestUrl(String url, String language) {
        // 1. Deduplicate by URL (no HTTP fetch required)
        Optional<DocumentSourceEntity> byUrl = sourceRepository.findBySourceUrl(url);
        if (byUrl.isPresent()) {
            DocumentSourceEntity e = byUrl.get();
            return new IngestionResult(e.getId(), URI.create(url).getHost() + ".html", 0, true);
        }

        try {
            byte[] html = Jsoup.connect(url)
                    .userAgent("Papyrus/0.1 (+https://github.com/darqlab/papyrus)")
                    .timeout(15_000)
                    .execute()
                    .bodyAsBytes();

            String filename = URI.create(url).getHost() + ".html";
            // 2. Ingest (also deduplicates by content hash internally)
            IngestionResult result = ingest(html, filename, language);

            // 3. Persist the source URL on the newly created entity
            if (!result.duplicate()) {
                sourceRepository.findById(result.sourceId()).ifPresent(e -> {
                    e.setSourceUrl(url);
                    sourceRepository.save(e);
                });
            }

            return result;

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

    /**
     * Re-ingest an archived source using its stored extracted text.
     * Creates a new source record (new UUID) pointing back to the original archive directory.
     * The original source record is left untouched.
     */
    @Transactional
    public IngestionResult reingest(UUID originalSourceId) {
        Source original = vectorStoreService.findById(originalSourceId);
        if (original == null) throw new IllegalArgumentException("Source not found: " + originalSourceId);
        if (original.archiveFilename() == null) throw new IllegalStateException("Source has no archived file: " + originalSourceId);

        // Resolve the actual archive directory — may be the original or a prior re-ingest's source
        UUID archiveDirId = original.archiveSourceId() != null ? original.archiveSourceId() : originalSourceId;

        String extractedText = archiveService.readExtractedText(archiveDirId, original.archiveFilename());
        if (extractedText == null || extractedText.isBlank()) {
            throw new IllegalStateException("Archived extracted text not found for: " + archiveDirId + "/" + original.archiveFilename());
        }

        UUID newSourceId = UUID.randomUUID();
        DocumentSourceEntity source = new DocumentSourceEntity(
                newSourceId, original.filename(), original.contentType(), null,
                "eng", IngestionStatus.PROCESSING);
        source.setArchiveFilename(original.archiveFilename());
        source.setArchiveSourceId(archiveDirId);
        // Propagate stored chunking config so re-ingest produces identical chunk boundaries
        ChunkingStrategy storedStrategy = original.chunkingStrategy() != null
                ? ChunkingStrategy.valueOf(original.chunkingStrategy()) : null;
        source.setChunkingStrategy(storedStrategy);
        source.setChunkingMaxTokens(original.chunkingMaxTokens());
        source.setChunkingOverlapTokens(original.chunkingOverlapTokens());
        sourceRepository.saveAndFlush(source);

        try {
            ExtractedText extracted = ExtractedText.of(extractedText);
            ChunkingConfig config = resolveConfig(storedStrategy, original.chunkingMaxTokens(), original.chunkingOverlapTokens());
            List<String> chunks = chunkingService.chunk(extracted, config);

            source.setPageCount(extracted.pageCount());

            if (!chunks.isEmpty()) {
                List<List<Float>> embeddings = chunks.stream()
                        .map(embeddingService::embed)
                        .toList();
                List<Integer> tokenCounts = chunks.stream()
                        .map(TokenEstimator::estimate)
                        .toList();
                vectorStoreService.storeChunks(newSourceId, chunks, embeddings, tokenCounts, null);
            }

            source.setStatus(IngestionStatus.DONE);
            sourceRepository.save(source);
            return new IngestionResult(newSourceId, original.filename(), chunks.size(), false);

        } catch (Exception e) {
            source.setStatus(IngestionStatus.FAILED);
            source.setError(e.getMessage());
            sourceRepository.save(source);
            throw new RuntimeException("Re-ingestion failed for source " + originalSourceId, e);
        }
    }

    public record IngestionResult(UUID sourceId, String filename, int chunkCount, boolean duplicate) {}

    /**
     * Check whether a duplicate already exists for the given content, based on SHA-256 hash.
     * Returns the existing sourceId if found, or empty if this content is new.
     * Used by the controller for an early synchronous check before spawning a virtual thread.
     */
    public Optional<UUID> findDuplicate(byte[] content) {
        String hash = ContentHasher.sha256(content);
        return sourceRepository.findByContentHash(hash).map(DocumentSourceEntity::getId);
    }

    private ChunkingConfig resolveConfig(ChunkingStrategy strategy, Integer maxTokens, Integer overlapTokens) {
        return new ChunkingConfig(
                strategy     != null ? strategy     : properties.chunking().strategy(),
                maxTokens    != null ? maxTokens    : properties.chunking().maxTokens(),
                overlapTokens != null ? overlapTokens : properties.chunking().overlapTokens()
        );
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

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
