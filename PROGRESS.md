# Papyrus — Progress

## Phase 1 — Core Domain + Digital Extractors (v0.1) ✅

**Completed:** 2026-04-09

### What was built

**papyrus-core** — pure Java domain layer (no Spring, no I/O):
- Domain records: `Document`, `DocumentChunk`, `IngestionJob`, `SearchResult`, `Source`
- Enums: `IngestionStatus` (PENDING/PROCESSING/DONE/FAILED), `JobStatus` (QUEUED/RUNNING/DONE/FAILED)
- Service interfaces: `DocumentIngestionService`, `SearchService`, `EmbeddingService`
- Utilities: `MimeTypeDetector`, `TextNormalizer`, `TokenEstimator`

**papyrus-extractor** — format routing and digital document extraction (no Spring):
- `DocumentExtractor` interface + `ExtractedText` record + `ExtractionException`
- `FormatRouter` — MIME-based dispatch with `withDefaultExtractors()` factory
- `DigitalPdfExtractor` — PDFBox page-by-page extraction
- `OfficeExtractor` — Apache POI DOCX
- `HtmlExtractor` — Jsoup
- `PlainTextExtractor` — TXT, MD, CSV

### Test results

| Module | Tests | Result |
|--------|-------|--------|
| papyrus-core | 37 | ✅ All pass |
| papyrus-extractor | 35 | ✅ All pass |
| **Total** | **72** | **✅ All pass** |

### Key decisions made
- Fixtures built programmatically in tests (PDFBox + POI) — no binary files in repo
- `ExtractedText.averageCharsPerPage()` ready for Phase 2 scanned PDF detection
- `FormatRouter` is Spring-free and injectable — wires cleanly as a Spring bean in Phase 5

---

## Phase 3 — Chunking + Voyage AI Embeddings + pgvector (v0.2) ✅

**Completed:** 2026-04-09

- `ChunkingService` — paragraph and fixed-size strategies with overlap
- `VoyageAiEmbeddingService` — REST client for `voyage-3-lite` (512-dim vectors)
- `VectorStoreService` — JDBC-based chunk storage + pgvector cosine similarity search
- Flyway schema: `document_sources`, `document_chunks` (HNSW index), `ingestion_jobs`
- Integration tests via Testcontainers (`pgvector/pgvector:pg16`)

---

## Phase 4 — MCP Server (v0.3) ✅

**Completed:** 2026-04-09

- Spring AI MCP server with SSE transport
- 6 tools: `ingest_document`, `ingest_url`, `search`, `list_sources`, `get_document`, `delete_source`

---

## Phase 5 — REST API + Web UI (v0.3) ✅

**Completed:** 2026-04-09

- `DocumentController` — upload, list, get, delete, URL ingest, batch ingest
- `SearchController` — semantic search with optional source filter
- `JobController` — batch job status tracking
- Single-page dashboard (`index.html`)

---

## Phase 6 — Batch Ingest + Job Tracking (v0.4) ✅

**Completed:** 2026-04-09

- `POST /api/documents/batch` — multi-file ingest with `IngestionJob` tracking
- `POST /api/documents/url` — Jsoup HTTP fetch + pipeline
- `DELETE /api/documents/{id}` — cascading delete (chunks + source)
- `GET /api/jobs/{id}` — job status polling

---

## Phase 8 — Extended Formats + OCR (v0.5) ✅

**Completed:** 2026-04-09

### New extractors

| Extractor | Formats | Notes |
|-----------|---------|-------|
| `SpreadsheetExtractor` | XLSX | Apache POI; sheet name + cell values |
| `PresentationExtractor` | PPTX | Apache POI; per-slide text |
| `EpubExtractor` | EPUB | ZIP + XHTML chapters via Jsoup |
| `OcrExtractor` | PDF (scanned) | Tess4J 300 DPI render → Tesseract |
| `SmartPdfExtractor` | PDF | Digital first; OCR fallback if < 100 chars/page |
| `ImageOcrExtractor` | PNG, JPG, TIFF, BMP, GIF | Direct image → Tesseract |

### OCR correction (Claude vision)

- `OcrCorrectionService` — post-processes Tesseract output using Claude Sonnet vision API
- Fixes misspellings, stray characters, noise, and word-boundary errors
- Enabled via `OCR_CORRECTION_ENABLED=true` + `ANTHROPIC_API_KEY`
- Fallback: if correction fails or is disabled, raw Tesseract output is used

### Test results

| Module | Tests | Result |
|--------|-------|--------|
| papyrus-extractor | 63 | ✅ All pass |

---

## Staging Deployment ✅

**Deployed:** 2026-04-09 · `http://localhost:8081`

- `Dockerfile` — `eclipse-temurin:21-jre-alpine` + `tesseract-ocr` + `tesseract-ocr-data-eng`
- `/opt/yard/papyrus/docker-compose.yml` — `papyrus-api` + `papyrus-pgvector` (dedicated pgvector/pgvector:pg16)
- Both on `proxy_default` network (pgAdmin accessible)
- `TESSDATA_PREFIX=/usr/share/tessdata` (Alpine path)

---

## UI — Two-Page App ✅

**Updated:** 2026-04-09

| Page | URL | Features |
|------|-----|---------|
| Ingest | `/ingest` | Upload + ingested docs side-by-side; 3-step image flow (select → verify → save); OCR comparison at 85vh |
| Search | `/search` | Semantic search; document filter dropdown; score bar |

### Image ingest flow (3 steps)
1. **Select** — pick image, thumbnail preview shown; "Extract & Verify" button appears
2. **Verify** — calls `POST /api/documents/preview` (no storage); side-by-side image vs extracted text at 85vh
3. **Save** — "Accept & Save" calls `POST /api/documents` to store + embed; "Discard" cancels

### New API endpoint
- `POST /api/documents/preview` — extract text without storing (used by verify step)

---

## Pending

| Phase | Scope | Status |
|-------|-------|--------|
| 7 | Ollama embedding provider | Skipped (user choice) |
| 9 | Auth (API key / OAuth2) | Planned |
| 10 | Production hardening (rate limiting, observability) | Planned |
