package io.darqlab.papyrus.api.controller;

import io.darqlab.papyrus.api.controller.dto.*;
import io.darqlab.papyrus.api.service.DocumentService;
import io.darqlab.papyrus.pipeline.config.ChunkingStrategy;
import io.darqlab.papyrus.core.domain.IngestionJob;
import io.darqlab.papyrus.core.domain.Source;
import io.darqlab.papyrus.pipeline.archive.ArchiveService;
import io.darqlab.papyrus.pipeline.job.IngestionJobService;
import io.darqlab.papyrus.pipeline.store.VectorStoreService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final VectorStoreService vectorStoreService;
    private final IngestionJobService jobService;
    private final ArchiveService archiveService;

    public DocumentController(DocumentService documentService,
                               VectorStoreService vectorStoreService,
                               IngestionJobService jobService,
                               ArchiveService archiveService) {
        this.documentService    = documentService;
        this.vectorStoreService = vectorStoreService;
        this.jobService         = jobService;
        this.archiveService     = archiveService;
    }

    /**
     * Upload and ingest a document asynchronously.
     * Returns immediately with a jobId that can be polled via GET /api/jobs/{id}.
     * <p>POST /api/documents</p>
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AsyncUploadResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "language", defaultValue = "eng") String language,
            @RequestParam(value = "extractedText", required = false) String extractedText,
            @RequestParam(value = "archiveFilename", required = false) String archiveFilename,
            @RequestParam(value = "chunkingStrategy", required = false) ChunkingStrategy chunkingStrategy,
            @RequestParam(value = "chunkingMaxTokens", required = false) Integer chunkingMaxTokens,
            @RequestParam(value = "chunkingOverlapTokens", required = false) Integer chunkingOverlapTokens)
            throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : file.getName();
        byte[] content  = file.getBytes();

        IngestionJob job = jobService.createJob(1);
        jobService.markRunning(job.id());

        Thread.ofVirtual().start(() -> {
            try {
                documentService.ingest(content, filename, language, extractedText, archiveFilename,
                        chunkingStrategy, chunkingMaxTokens, chunkingOverlapTokens);
                jobService.recordSuccess(job.id());
                jobService.markDone(job.id());
            } catch (Exception e) {
                jobService.recordFailure(job.id());
                jobService.markFailed(job.id());
            }
        });

        return ResponseEntity.accepted().body(new AsyncUploadResponse(job.id(), filename, "QUEUED"));
    }

    /**
     * List all ingested documents.
     * <p>GET /api/documents?limit=20&offset=0</p>
     */
    @GetMapping
    public ResponseEntity<List<SourceResponse>> list(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0")  int offset) {

        List<SourceResponse> sources = vectorStoreService.listSources(limit, offset)
                .stream()
                .map(s -> new SourceResponse(s.id(), s.filename(), s.archiveFilename(), s.archiveSourceId(),
                        s.contentType(), s.status(), s.createdAt(),
                        s.chunkingStrategy(), s.chunkingMaxTokens(), s.chunkingOverlapTokens()))
                .toList();

        return ResponseEntity.ok(sources);
    }

    /**
     * Get a specific document by ID.
     * <p>GET /api/documents/{id}</p>
     */
    @GetMapping("/{id}")
    public ResponseEntity<SourceResponse> get(@PathVariable UUID id) {
        Source s = vectorStoreService.findById(id);
        if (s == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(new SourceResponse(
                s.id(), s.filename(), s.archiveFilename(), s.archiveSourceId(),
                s.contentType(), s.status(), s.createdAt(),
                s.chunkingStrategy(), s.chunkingMaxTokens(), s.chunkingOverlapTokens()));
    }

    /**
     * Delete a document and all its chunks.
     * <p>DELETE /api/documents/{id}</p>
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        return documentService.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /**
     * Extract text from a file without storing — for OCR preview/verification.
     * <p>POST /api/documents/preview</p>
     */
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PreviewResponse> preview(
            @RequestPart("file") MultipartFile file) throws IOException {

        if (file.isEmpty()) return ResponseEntity.badRequest().build();

        String filename = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : file.getName();
        String text = documentService.preview(file.getBytes(), filename);
        String suggestedFilename = archiveService.buildArchiveFilename(text, filename);
        return ResponseEntity.ok(new PreviewResponse(filename, text, suggestedFilename));
    }

    /**
     * Ingest a document from a URL.
     * <p>POST /api/documents/url</p>
     */
    @PostMapping("/url")
    public ResponseEntity<UploadResponse> ingestUrl(@RequestBody UrlIngestRequest request) {
        if (request.url() == null || request.url().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String lang = request.language() != null ? request.language() : "eng";
        DocumentService.IngestionResult result = documentService.ingestUrl(request.url(), lang);
        return ResponseEntity.ok(new UploadResponse(
                result.sourceId(), result.filename(), result.chunkCount(), "DONE"));
    }

    /**
     * Batch-ingest multiple files, tracked via an IngestionJob.
     * <p>POST /api/documents/batch</p>
     */
    @PostMapping(value = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BatchUploadResponse> batch(
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(value = "language", defaultValue = "eng") String language) throws IOException {

        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        IngestionJob job = jobService.createJob(files.size());
        jobService.markRunning(job.id());

        List<UploadResponse> results = new ArrayList<>();
        int failed = 0;

        for (MultipartFile file : files) {
            try {
                String filename = file.getOriginalFilename() != null
                        ? file.getOriginalFilename() : file.getName();
                DocumentService.IngestionResult r =
                        documentService.ingest(file.getBytes(), filename, language);
                results.add(new UploadResponse(r.sourceId(), r.filename(), r.chunkCount(), "DONE"));
                jobService.recordSuccess(job.id());
            } catch (Exception e) {
                jobService.recordFailure(job.id());
                failed++;
            }
        }

        jobService.markDone(job.id());

        return ResponseEntity.ok(new BatchUploadResponse(
                job.id(), files.size(), files.size() - failed, failed,
                failed == 0 ? "DONE" : "PARTIAL", results));
    }

    /**
     * Serve the archived original file for a document.
     * Browser-renderable types (images, PDF) are served inline; others trigger a download.
     * Transparently resolves the archive directory via archive_source_id for re-ingested sources.
     * <p>GET /api/documents/{id}/file</p>
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable UUID id) {
        Source source = vectorStoreService.findById(id);
        if (source == null || source.archiveFilename() == null) return ResponseEntity.notFound().build();

        // Re-ingested sources point to the original source's archive directory
        UUID archiveDirId = source.archiveSourceId() != null ? source.archiveSourceId() : id;
        Path file = archiveService.findOriginalFile(archiveDirId, source.archiveFilename());
        if (file == null) return ResponseEntity.notFound().build();

        String name = file.getFileName().toString().toLowerCase();
        MediaType mediaType;
        boolean inline;
        if (name.endsWith(".pdf")) {
            mediaType = MediaType.APPLICATION_PDF;
            inline = true;
        } else if (name.endsWith(".png")) {
            mediaType = MediaType.IMAGE_PNG;
            inline = true;
        } else if (name.endsWith(".gif")) {
            mediaType = MediaType.IMAGE_GIF;
            inline = true;
        } else if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".tiff") || name.endsWith(".bmp")) {
            mediaType = MediaType.parseMediaType("image/jpeg");
            inline = true;
        } else {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
            inline = false;
        }

        String disposition = (inline ? "inline" : "attachment") + "; filename=\"" + file.getFileName() + "\"";
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header("Content-Disposition", disposition)
                .body(new PathResource(file));
    }

    /**
     * Re-ingest an archived source with the current embedding model.
     * Creates a new source record; the original is preserved.
     * <p>POST /api/documents/{id}/reingest</p>
     */
    @PostMapping("/{id}/reingest")
    public ResponseEntity<AsyncUploadResponse> reingest(@PathVariable UUID id) {
        Source source = vectorStoreService.findById(id);
        if (source == null) return ResponseEntity.notFound().build();
        if (source.archiveFilename() == null)
            return ResponseEntity.badRequest().build();

        IngestionJob job = jobService.createJob(1);
        jobService.markRunning(job.id());

        Thread.ofVirtual().start(() -> {
            try {
                documentService.reingest(id);
                jobService.recordSuccess(job.id());
                jobService.markDone(job.id());
            } catch (Exception e) {
                jobService.recordFailure(job.id());
                jobService.markFailed(job.id());
            }
        });

        return ResponseEntity.accepted().body(new AsyncUploadResponse(job.id(), source.filename(), "QUEUED"));
    }
}
