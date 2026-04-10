package io.darqlab.papyrus.api.controller;

import io.darqlab.papyrus.api.controller.dto.*;
import io.darqlab.papyrus.api.service.DocumentService;
import io.darqlab.papyrus.core.domain.IngestionJob;
import io.darqlab.papyrus.pipeline.job.IngestionJobService;
import io.darqlab.papyrus.pipeline.store.VectorStoreService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final VectorStoreService vectorStoreService;
    private final IngestionJobService jobService;

    public DocumentController(DocumentService documentService,
                               VectorStoreService vectorStoreService,
                               IngestionJobService jobService) {
        this.documentService    = documentService;
        this.vectorStoreService = vectorStoreService;
        this.jobService         = jobService;
    }

    /**
     * Upload and ingest a document asynchronously.
     * Returns immediately with a jobId that can be polled via GET /api/jobs/{id}.
     * <p>POST /api/documents</p>
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AsyncUploadResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "language", defaultValue = "eng") String language) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : file.getName();
        byte[] content  = file.getBytes();

        IngestionJob job = jobService.createJob(1);
        jobService.markRunning(job.id());

        Thread.ofVirtual().start(() -> {
            try {
                documentService.ingest(content, filename, language);
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
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0")  int offset) {

        List<SourceResponse> sources = vectorStoreService.listSources(limit, offset)
                .stream()
                .map(s -> new SourceResponse(s.id(), s.filename(), s.contentType(),
                        s.status(), s.createdAt()))
                .toList();

        return ResponseEntity.ok(sources);
    }

    /**
     * Get a specific document by ID.
     * <p>GET /api/documents/{id}</p>
     */
    @GetMapping("/{id}")
    public ResponseEntity<SourceResponse> get(@PathVariable UUID id) {
        return vectorStoreService.listSources(Integer.MAX_VALUE, 0)
                .stream()
                .filter(s -> s.id().equals(id))
                .findFirst()
                .map(s -> ResponseEntity.ok(new SourceResponse(
                        s.id(), s.filename(), s.contentType(), s.status(), s.createdAt())))
                .orElse(ResponseEntity.notFound().build());
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
        return ResponseEntity.ok(new PreviewResponse(filename, text));
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
}
