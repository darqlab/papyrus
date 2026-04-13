package io.darqlab.papyrus.api.service;

import io.darqlab.papyrus.core.domain.ExtractedText;
import io.darqlab.papyrus.core.domain.IngestionStatus;
import io.darqlab.papyrus.core.domain.Source;
import io.darqlab.papyrus.core.service.EmbeddingService;
import io.darqlab.papyrus.core.util.MimeTypeDetector;
import io.darqlab.papyrus.core.util.TokenEstimator;
import io.darqlab.papyrus.extractor.FormatRouter;
import io.darqlab.papyrus.pipeline.archive.ArchiveService;
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
    private final ArchiveService archiveService;

    public DocumentService(FormatRouter formatRouter,
                           ChunkingService chunkingService,
                           EmbeddingService embeddingService,
                           VectorStoreService vectorStoreService,
                           DocumentSourceRepository sourceRepository,
                           OcrCorrectionService ocrCorrectionService,
                           ArchiveService archiveService) {
        this.formatRouter          = formatRouter;
        this.chunkingService       = chunkingService;
        this.embeddingService      = embeddingService;
        this.vectorStoreService    = vectorStoreService;
        this.sourceRepository      = sourceRepository;
        this.ocrCorrectionService  = ocrCorrectionService;
        this.archiveService        = archiveService;
    }

    @Transactional
    public IngestionResult ingest(byte[] content, String filename, String language) {
        return ingest(content, filename, language, null, null);
    }

    @Transactional
    public IngestionResult ingest(byte[] content, String filename, String language, String preExtractedText) {
        return ingest(content, filename, language, preExtractedText, null);
    }

    @Transactional
    public IngestionResult ingest(byte[] content, String filename, String language, String preExtractedText, String archiveFilename) {
        String mimeType = MimeTypeDetector.detect(filename);
        UUID sourceId   = UUID.randomUUID();

        DocumentSourceEntity source = new DocumentSourceEntity(
                sourceId, filename, mimeType, (long) content.length,
                language, IngestionStatus.PROCESSING);
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
        sourceRepository.saveAndFlush(source);

        try {
            ExtractedText extracted = ExtractedText.of(extractedText);
            List<String> chunks = chunkingService.chunk(extracted);

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
            return new IngestionResult(newSourceId, original.filename(), chunks.size());

        } catch (Exception e) {
            source.setStatus(IngestionStatus.FAILED);
            source.setError(e.getMessage());
            sourceRepository.save(source);
            throw new RuntimeException("Re-ingestion failed for source " + originalSourceId, e);
        }
    }

    public record IngestionResult(UUID sourceId, String filename, int chunkCount) {}

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
