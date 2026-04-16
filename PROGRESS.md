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

## UI Improvements — Phase 1 (Quick Wins) ✅

**Completed:** 2026-04-10

### chat.html
- **New Conversation button** — sidebar button clears all messages and resets `history[]` with a confirmation prompt; re-focuses textarea
- **Copy button** — appears on hover on each assistant bubble (top-right, `position:absolute`); copies raw Markdown via `navigator.clipboard.writeText()`; shows "Copied!" for 1.5s then restores
- **Message timestamps** — `makeTimestamp()` appends `<span class="msg-time">` below each bubble at send/render time; right-aligned for user, left-aligned for assistant
- **Character counter** — live `#char-count` below textarea; turns orange when >2 000 chars; resets to 0 on send
- **Health indicator dot** — sidebar footer dot polls `/actuator/health` on load and every 60s; green (`health-ok`), amber (`health-warn`), red (`health-err`)

### ingest.html
- **Drag and drop upload** — `#drag-overlay` (fixed, full-screen, dashed border) shown on `dragenter`; dropped file validated by extension and injected into existing `onFileSelected()` via `DataTransfer` API
- **Filter and search table** — `#filter-name` (text search) and `#filter-status` (All/Done/Failed/Processing) filter `allDocs` array client-side in real time; "No results" message when empty
- **Health indicator dot** — identical to `chat.html`

### Infrastructure
- `WebConfig.java` updated: `/` and `/chat` → `chat.html`, `/ingest` → `ingest.html`; `/search` removed
- Sidebar layout applied to both pages (replaces previous top-nav header)
- Rebuilt and redeployed: Maven + Docker image + `docker compose up -d papyrus-api`

---

## UI Improvements — Phase 2 (Backend-Dependent Features) ✅

**Completed:** 2026-04-10

### Delete Document (`ingest.html` + backend)
- `DELETE /api/documents/{id}` and `DocumentService.delete()` were already in place
- Added Delete button to each row in the documents table; removes row from DOM on 204 without reload

### Source Citations Panel (`chat.html` + `ChatController`)
- `ChatController` now emits `event: sources` SSE event (JSON `[{filename, excerpt}]`, excerpt ≤200 chars) before `event: done`
- `chat.html` parses `sources` event and renders a collapsible `<details>` "Sources" panel below each assistant bubble
- Messages without a `sources` event render unchanged (backwards compatible)

### Per-Document Chat Scope (`chat.html` + `ChatController`)
- `ChatRequest` record extended with optional `String sourceId`
- `ChatController` converts `sourceId` to UUID and passes it to `VectorStoreService.searchByVector()`
- Document selector dropdown in `chat.html` (above textarea) populated from `GET /api/documents` on load
- Selected `sourceId` included in all chat requests; null = all documents

### Ingest Progress Bar (`ingest.html` + `DocumentController`)
- `POST /api/documents` made async: creates an `IngestionJob` (total=1), fires a virtual thread for processing, returns `AsyncUploadResponse{jobId, filename, status:"QUEUED"}` (HTTP 202) immediately
- `ingest.html`: `pollJob()` polls `GET /api/jobs/{id}` every 2s; updates progress bar (`#progress-bar`) width; stops and shows result on `DONE` or `FAILED`
- Both `uploadDirect()` and `acceptAndSave()` use the polling flow

---

---

## Bug Fixes & Improvements ✅

**Completed:** 2026-04-12

### #6 — Ingested docs list order
- `VectorStoreService.listSources()` now sorts by `createdAt` DESC (newest first)
- Default API limit changed from 20 → 50
- UI fetch limit changed from 200 → 50

### #8 — Chat bubble alignment
- Added `width: 100%` to `.msg` container so `align-items: flex-end` on user bubbles correctly pushes them to the right
- User messages right-aligned, assistant messages left-aligned — matches Claude chat layout

---

## Infrastructure — Independent pgvector ✅

**Completed:** 2026-04-12

### Two-compose local development setup
- `docker-compose.local.yml` — builds `papyrus-api` and `papyrus-mcp` from local source; connects to external `papyrus-db` network
- `/opt/yard/pgvector/docker-compose.yml` — standalone `pgvector/pgvector:pg16` instance; runs independently, never restarted by app rebuilds
- Named Docker volume `pgvector_data` for persistent embeddings storage
- `initdb/01_init.sql` enables `vector` extension and grants schema permissions on first start
- `.env` / `.env.example` pattern following production hardening guide

### OCR correction enabled by default in local env
- `OCR_CORRECTION_ENABLED=true` set in local `.env`
- `.env` added to `.gitignore`

---

## MCP Transport — Streamable HTTP ✅

**Completed:** 2026-04-15

Migrated the MCP server from the deprecated SSE transport to the current Streamable HTTP transport, making it compatible with claude.ai browser chat, Claude mobile, and Claude Desktop.

### What changed

| File | Change |
|------|--------|
| `pom.xml` | Spring Boot `3.3.5` → `3.5.13`; Spring AI `1.0.0` → `1.1.4` |
| `papyrus-mcp/src/main/resources/application.yml` | Added `spring.ai.mcp.server.protocol: STREAMABLE` |
| `papyrus-mcp/pom.xml` | Updated description |

### Why Spring Boot had to be upgraded too

Spring AI 1.0.x (all patch releases) uses MCP Java SDK 0.10.0, which only includes `WebMvcSseServerTransportProvider` (SSE). Streamable HTTP (`WebMvcStreamableServerTransportProvider`) was added in MCP SDK 0.16+, which is only available in Spring AI 1.1.x. Spring AI 1.1.x requires Spring Boot 3.5.x, so both had to move together.

### Transport comparison

| | SSE (old) | Streamable HTTP (new) |
|--|-----------|----------------------|
| Connect endpoint | `GET /sse` | — |
| Message endpoint | `POST /mcp/message?sessionId=...` | `POST /mcp` |
| Session model | Stateful (session ID per connection) | Stateless-friendly |
| claude.ai / mobile | Not supported | Supported via Settings → Connectors |
| MCP spec | 2024-11-05 | 2025-03-26 |

### Key decisions

- Stayed on `spring-ai-starter-mcp-server-webmvc` — same dependency, transport is now selected via the `protocol` property rather than a separate artifact
- No code changes required in `McpConfig`, `IngestTools`, or `SearchTools` — the Spring AI tool API is stable across 1.0→1.1

---

## Credit Exhausted Indicator ✅

**Completed:** 2026-04-16

When the Anthropic API account has no remaining credits, the chat UI now shows a clear amber warning banner instead of a generic red error.

### What was built

| Layer | Change |
|-------|--------|
| `papyrus-core` | New `CreditExhaustedException` (unchecked, two constructors) |
| `papyrus-pipeline` | `AnthropicChatService` — detects HTTP 402 and billing 429; wraps as `CreditExhaustedException` at both eager (createStreaming) and lazy (flatMap) throw points |
| `papyrus-api` | `ChatController` — dedicated `catch (CreditExhaustedException)` block emits `event: credit_exhausted` SSE event then calls `emitter.complete()` |
| `papyrus-api` (UI) | `chat.html` — new `.credit-exhausted-banner` CSS (amber) and `credit_exhausted` SSE branch; clears thinking bubble, shows banner with link to Anthropic billing console, cleans up `history[]` |

### Key decisions

- Used `AnthropicServiceException` base class (not separate subtypes) for a single-catch approach covering 402 and billing 429
- Two-point wrapping in `streamChat()` covers both eager and lazy SDK throws
- `emitter.complete()` (not `completeWithError`) ensures the event is flushed before the response closes
- Banner is amber (`#fffbeb` / `#f59e0b`) — visually distinct from the existing red generic error span

---

## Pending

| # | Scope | Status |
|---|-------|--------|
| #4 | Duplicate entry detection | Planned — plan at `/home/dennis/devops/projects/papyrus/docs/PLAN_DuplicateHandling.md` |
| #5 | Edit OCR verification text | Planned |
| #7 | Rename ingested image from content | Planned |
| — | Credit exhausted indicator (chat UI) | ✅ Done — `feat/credit-exhausted-indicator` |
| — | Auth (API key / OAuth2) | Planned |
| — | Production hardening (rate limiting, observability) | Planned |
