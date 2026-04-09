package io.darqlab.papyrus.api.controller;

import io.darqlab.papyrus.api.controller.dto.SourceResponse;
import io.darqlab.papyrus.api.controller.dto.UploadResponse;
import io.darqlab.papyrus.api.service.DocumentService;
import io.darqlab.papyrus.pipeline.store.VectorStoreService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final VectorStoreService vectorStoreService;

    public DocumentController(DocumentService documentService,
                               VectorStoreService vectorStoreService) {
        this.documentService   = documentService;
        this.vectorStoreService = vectorStoreService;
    }

    /**
     * Upload and ingest a document.
     * <p>POST /api/documents</p>
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "language", defaultValue = "eng") String language) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        DocumentService.IngestionResult result = documentService.ingest(
                file.getBytes(),
                file.getOriginalFilename() != null ? file.getOriginalFilename() : file.getName(),
                language);

        return ResponseEntity.ok(new UploadResponse(
                result.sourceId(), result.filename(), result.chunkCount(), "DONE"));
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
}
