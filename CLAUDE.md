# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> Global preferences: see /home/dennis/CLAUDE.md

## Documentation Methodology

> **MANDATORY — Do this before writing any code.**
> Read the relevant docs in `/home/dennis/devops/projects/papyrus/docs/` and `/home/dennis/devops/methodology/` before starting any task. If the required IA/IP/TM docs do not exist yet, create them first and confirm with the user before proceeding to implementation.

All feature work follows the 7-phase methodology defined at `/home/dennis/devops/methodology/`. Before implementing any feature or fix, create the required docs in `/home/dennis/devops/projects/papyrus/docs/` using the naming convention `{Module}_{Feature}_{Type}.md`.

### Required docs per feature

| Type | Acronym | When required | Template |
|------|---------|--------------|---------|
| Issue Analysis | IA | Always — describes the problem | — |
| Technical Assessment | TA | Alternative to IA for bug/regression analysis | — |
| Implementation Plan | IP | Always — step-by-step plan with file list | `PLAN_*.md` (current convention) |
| Feature Rationale | FR | New features needing justification | — |
| Task Management | TM | Always — tracks phase progress | — |
| Architecture Decision Record | ADR | When a significant design decision is made | `ADR_{NNN}_{Title}.md` |
| QA Checklist | QA | Post-implementation, recommended | — |

### Compliance checklist

Use `/home/dennis/devops/methodology/Documentation_Compliance_Checklist.md` to verify all required artefacts exist before marking a feature done and updating `PROGRESS.md`.

### Key methodology references

| File | Purpose |
|------|---------|
| `Development_Methodology_Guide.md` | End-to-end 7-phase lifecycle |
| `Documentation_Compliance_Checklist.md` | Pre/during/post checklist per feature |
| `java_application_structural_guide.md` | Java layering and module conventions |
| `java_naming_convention_guide.md` | Java naming rules |
| `TDD_Specification.md` | Test-driven development process |
| `Project_Technical_Documentation.md` | Full project-level doc template |
| `Document_Style_Guide.md` | Writing style for all docs |

### Existing plan docs

All docs live in `/home/dennis/devops/projects/papyrus/docs/`.

| File | Feature |
|------|---------|
| `PLAN_DuplicateHandling.md` | Duplicate document detection (#4) — IP only |
| `PLAN_CreditExhaustedIndicator.md` | Credit exhausted indicator — detailed code plan (✅ complete) |
| `Papyrus_CreditExhaustedIndicator_IA.md` | Credit exhausted indicator — Issue Analysis (✅ complete) |
| `Papyrus_CreditExhaustedIndicator_FR.md` | Credit exhausted indicator — Feature Rationale (✅ complete) |
| `Papyrus_CreditExhaustedIndicator_IP.md` | Credit exhausted indicator — Implementation Plan (✅ complete) |
| `Papyrus_CreditExhaustedIndicator_TM.md` | Credit exhausted indicator — Task Management (✅ complete) |
| `Papyrus_FileSizeWarning_IA.md` | File size warning (#10) — Issue Analysis |
| `Papyrus_FileSizeWarning_IP.md` | File size warning (#10) — Implementation Plan |
| `Papyrus_FileSizeWarning_TM.md` | File size warning (#10) — Task Management |
| `Papyrus_EvolinkChatProvider_IA.md` | Evolink AI chat provider — Issue Analysis |
| `Papyrus_EvolinkChatProvider_IP.md` | Evolink AI chat provider — Implementation Plan |
| `Papyrus_EvolinkChatProvider_TM.md` | Evolink AI chat provider — Task Management |
| `Papyrus_OcrProviders_IA.md` | Configurable OCR correction providers — Issue Analysis |
| `Papyrus_OcrProviders_IP.md` | Configurable OCR correction providers — Implementation Plan |
| `Papyrus_OcrProviders_TM.md` | Configurable OCR correction providers — Task Management |
| `Papyrus_PerSourceChunkingStrategy_IA.md` | Per-source chunking strategy (PARAGRAPH/SECTION/SEMANTIC) — Issue Analysis (✅ complete) |
| `Papyrus_PerSourceChunkingStrategy_IP.md` | Per-source chunking strategy — Implementation Plan (✅ complete) |
| `Papyrus_PerSourceChunkingStrategy_TM.md` | Per-source chunking strategy — Task Management (✅ complete) |

---

## Build & Test Commands

```bash
# Build all modules (skip tests)
mvn -pl papyrus-core,papyrus-extractor,papyrus-pipeline,papyrus-api,papyrus-mcp install -DskipTests

# Full build with tests
mvn verify

# Run tests for a single module
mvn -pl papyrus-core test
mvn -pl papyrus-extractor test
mvn -pl papyrus-pipeline test
mvn -pl papyrus-api test
mvn -pl papyrus-mcp test

# Run a single test class
mvn -pl papyrus-extractor test -Dtest=SmartPdfExtractorTest

# Build the API fat JAR (required before building Docker image)
mvn -pl papyrus-core,papyrus-extractor,papyrus-pipeline,papyrus-api install -DskipTests

# Build Docker image for papyrus-api
docker build -t papyrus-api:staging .

# Build Docker image for papyrus-mcp
docker build -f Dockerfile.mcp -t papyrus-mcp:staging .
```

The integration tests in `papyrus-pipeline` and `papyrus-api` use **Testcontainers** (spins up `pgvector/pgvector:pg16` automatically — requires Docker running).

## Local Dev (Docker Compose)

**Testing directory: `/opt/yard/papyrus/`** — use this for all local test runs. It contains `docker-compose.local.yml` which builds from `/home/dennis/Projects/papyrus` and reads real API keys from `/opt/yard/papyrus/.env`.

```bash
# Build the JAR first (from project root)
mvn -pl papyrus-core,papyrus-extractor,papyrus-pipeline,papyrus-api install -DskipTests

# Rebuild and restart the API (from testing dir)
docker compose -f /opt/yard/papyrus/docker-compose.local.yml up -d --build papyrus-api

# Tail logs
docker compose -f /opt/yard/papyrus/docker-compose.local.yml logs -f papyrus-api

# Stop app
docker compose -f /opt/yard/papyrus/docker-compose.local.yml down
```

Two compose files in the project root (for reference / CI use):

| File | Purpose |
|------|---------|
| `docker-compose.yml` | App services — builds from source, bind-mounts archive |
| `docker-compose.db.yml` | Standalone pgvector — run once, leave running |

The archive is bind-mounted to `./volumes/archive/` (visible on host).  
Both compose files use the external `papyrus-db` Docker network created by `docker-compose.db.yml`.

## Architecture

Papyrus is a self-hosted document intelligence MCP server used to ingest, index, and semantically search **committee meeting documents** (scanned minutes, resolutions, and records). It has two runnable entry points and four supporting library modules:

```
papyrus-core        — Spring-free domain layer (records, enums, service interfaces, utilities)
papyrus-extractor   — Spring-free format routing and text extraction (no I/O side effects)
papyrus-pipeline    — Spring beans: chunking, embeddings, pgvector storage, job tracking, archive, chat providers
papyrus-api         — Spring Boot REST API (port 8080/8081 in staging) — main runnable
papyrus-mcp         — Spring Boot MCP server (Streamable HTTP transport, Spring AI) — second runnable
```

Static HTML/JS UI is served by `papyrus-api` from `src/main/resources/static/`.

### Ingest flow (papyrus-api)

```
DocumentController → DocumentService → FormatRouter → extractor impl
  → OcrCorrectionService (optional, Claude Sonnet)
  → ArchiveService (saves original + extracted text to filesystem)
  → ChunkingService → VoyageAiEmbeddingService → VectorStoreService (pgvector)
```

Archive is the **source of truth**. Embeddings are derived and can be rebuilt via re-ingestion.

### Search flow

`SearchController` → `VoyageAiEmbeddingService.embed(query)` → `VectorStoreService.searchByVector()` → cosine similarity via pgvector HNSW index

### Format routing (`papyrus-extractor`)

`FormatRouter` dispatches by MIME type to:
- `SmartPdfExtractor` — digital PDF first; falls back to OCR if avg chars/page < 100
- `OcrExtractor` / `ImageOcrExtractor` — Tess4J (Tesseract), 300 DPI
- `OfficeExtractor` (DOCX), `SpreadsheetExtractor` (XLSX), `PresentationExtractor` (PPTX)
- `HtmlExtractor` (Jsoup), `PlainTextExtractor` (TXT/MD/CSV), `EpubExtractor` (ZIP+XHTML)

### Database schema (Flyway)

| Migration | Description |
|-----------|-------------|
| `V1__create_schema.sql` | `document_sources`, `document_chunks` (`vector(512)`), `ingestion_jobs`, HNSW index |
| `V2__add_archive_filename.sql` | `archive_filename TEXT` on `document_sources` |
| `V3__add_archive_source_id.sql` | `archive_source_id UUID` — links re-ingested records to original archive dir |

Embeddings are 512-dimensional (`voyage-3-lite`). Changing embedding providers requires a schema migration and full re-ingestion.

### Archive layout

```
volumes/archive/
  {sourceId}/
    {archiveFilename}.{ext}   ← original file (all types)
    {archiveFilename}.txt     ← extracted text
```

- OCR files (images): filename derived from content — `{date}-{slug}-{page}`
- Non-OCR files (PDF, DOCX, etc.): original filename stem retained

Re-ingestion reads from `.txt`, skips re-extraction, rebuilds embeddings only.

### Chat flow (papyrus-api)

```
ChatController → EmbeddingService.embed(query) → VectorStoreService.searchByVector() → append excerpts to system prompt
  → ChatService.streamChat(turns, systemPrompt) → SSE token stream
```

`ChatService` is a `papyrus-core` interface. Provider is selected at startup via `CHAT_PROVIDER`:

| Bean | Provider | Condition |
|------|----------|-----------|
| `AnthropicChatService` | Anthropic SDK streaming | `CHAT_PROVIDER=anthropic` (default) |
| `OllamaChatService` | Ollama `/api/chat` NDJSON stream | `CHAT_PROVIDER=ollama` |

System prompt is loaded once at startup by `PromptLoader`:
1. If `CHAT_PROMPT_FILE` is set → load from that path (fail fast if missing or blank)
2. Otherwise → load from classpath `prompts/chat-system.md`

Same pattern applies to OCR correction via `OCR_PROMPT_FILE` / `prompts/ocr-correction.md`.

### MCP server (`papyrus-mcp`)

Runs separately from the REST API. Tools defined in `IngestTools` and `SearchTools` using Spring AI `@Tool` annotations. Both runnables share the same `papyrus-pipeline` and `papyrus-extractor` modules and connect to the same PostgreSQL database.

Transport: **Streamable HTTP** (`spring.ai.mcp.server.protocol: STREAMABLE`), endpoint `POST /mcp`. Requires Spring AI 1.1.x + Spring Boot 3.5.x. Compatible with claude.ai, Claude mobile, and Claude Code (`--transport http`).

## Key Configuration

| Env var | Purpose |
|---------|---------|
| `VOYAGE_API_KEY` | Required — Voyage AI embeddings |
| `VOYAGE_MODEL` | Embedding model (default: `voyage-3-lite`); alternatives: `voyage-large-2-1`, `voyage-2` |
| `ANTHROPIC_API_KEY` | Required when `CHAT_PROVIDER=anthropic` or `OCR_PROVIDER=anthropic` |
| `DATABASE_URL` | PostgreSQL JDBC URL (default: `jdbc:postgresql://localhost:5432/papyrus`) |
| `OCR_CORRECTION_ENABLED` | `true` to enable LLM post-processing of Tesseract output |
| `OCR_PROVIDER` | OCR correction provider: `anthropic` (default), `ollama`, or `evolink`. Independent of `CHAT_PROVIDER` |
| `OCR_CORRECTION_MODEL` | Model for OCR correction (default: `claude-sonnet-4-6`; set to match chosen provider) |
| `OCR_OLLAMA_BASE_URL` | Ollama base URL for OCR (default: `http://localhost:11434`; only when `OCR_PROVIDER=ollama`) |
| `ARCHIVE_ENABLED` | `true` to save originals + extracted text to filesystem |
| `ARCHIVE_PATH` | Archive root directory (default: `/data/papyrus/archive`) |
| `TESSDATA_PREFIX` | Tesseract data path (Alpine Docker: `/usr/share/tessdata`) |
| `CHAT_PROVIDER` | Chat LLM provider: `anthropic` (default), `ollama`, or `evolink` |
| `CHAT_MODEL` | Model name for the selected provider (default: `claude-opus-4-6`) |
| `CHAT_PROMPT_FILE` | Optional path to an external chat system prompt file (≥ 50 chars); restart required |
| `OCR_PROMPT_FILE` | Optional path to an external OCR correction prompt file; restart required |
| `CHAT_OLLAMA_BASE_URL` | Ollama base URL (default: `http://localhost:11434`; only used when `CHAT_PROVIDER=ollama`) |
| `CHAT_OLLAMA_MODEL` | Ollama model name (default: `llama3.2`; only used when `CHAT_PROVIDER=ollama`) |
| `EVOLINK_API_KEY` | Required when `CHAT_PROVIDER=evolink` or `OCR_PROVIDER=evolink` — Evolink AI bearer token (`sk-evo-...`) |
| `EVOLINK_BASE_URL` | Evolink base URL (default: `https://direct.evolink.ai`; only used when `CHAT_PROVIDER=evolink`) |
| `CHAT_EVOLINK_MODEL` | Evolink model name (default: `evolink/auto`; only used when `CHAT_PROVIDER=evolink`) |
| `CHUNKING_STRATEGY` | Global chunking strategy: `PARAGRAPH` (default), `SECTION`, `SEMANTIC`, `FIXED` |
| `CHUNKING_MAX_TOKENS` | Global max tokens per chunk (default: `512`) |
| `CHUNKING_OVERLAP_TOKENS` | Global overlap tokens between chunks (default: `64`) |
| `CHUNKING_SEMANTIC_THRESHOLD` | Cosine similarity breakpoint for SEMANTIC strategy (default: `0.75`) |
| `CHUNKING_SEMANTIC_MIN_SENTENCES` | Minimum sentences before a semantic break (default: `3`) |
| `CHUNKING_SECTION_PATTERN` | Regex for section header detection in SECTION strategy (default: `^\d{3}(\.\d{2,3})+\s+\S`) |
| `CHUNKING_SECTION_MIN_TOKENS` | Minimum tokens before merging small sections (default: `50`) |

## LLM & Embedding Model Selection

OCR correction, chat, and embedding services use configurable models. Model selection affects cost, speed, and capability.

| Service | Default Model | Alternatives | Config |
|---------|---------------|--------------|--------|
| **Embedding (Voyage)** | `voyage-3-lite` (512 dims, balanced) | `voyage-large-2-1`, `voyage-2` | `VOYAGE_MODEL` |
| **OCR Correction** | `claude-sonnet-4-6` (fastest, good vision) | `claude-opus-4-7`, `claude-haiku-4-5-20251001` | `OCR_CORRECTION_MODEL` |
| **Chat** | `claude-opus-4-6` (best reasoning) | `claude-sonnet-4-6`, `claude-haiku-4-5-20251001` | `CHAT_MODEL` (Anthropic) or `CHAT_OLLAMA_MODEL` (Ollama) |

To change any model, restart with a different env var:

```bash
# Use Voyage Large for embeddings (better quality, slower)
docker compose -e VOYAGE_MODEL=voyage-large-2-1 up -d papyrus-api

# Use Haiku for chat (cheaper)
docker compose -e CHAT_MODEL=claude-haiku-4-5-20251001 up -d papyrus-api

# Use Opus for OCR (higher quality, slower)
docker compose -e OCR_CORRECTION_MODEL=claude-opus-4-7 up -d papyrus-api
```

**Note:** Changing the embedding model requires re-ingestion of all documents to rebuild embeddings.
Changing chat or OCR models does not require re-ingestion.

## Web UI Pages

| URL | File | Purpose |
|-----|------|---------|
| `/chat` | `chat.html` | RAG chat with source citations and PDF export |
| `/ingest` | `ingest.html` | Upload files; OCR preview/verify flow for images |
| `/documents` | `documents.html` | List, filter, delete ingested documents |
| `/manage` | `manage.html` | Archive manager — re-ingest with current embedding model |

## Staging Deployment

Running stack managed at `/opt/yard/papyrus/` (separate from this repo):
- `papyrus-api` → `localhost:8081` (public: `https://papyrus.darqlab.net`)
- `papyrus-mcp` → `localhost:8082` (public: `https://mcp-papyrus.darqlab.net`)
- `papyrus-pgvector` (pgvector/pgvector:pg16)

Production images: `dennisarq/papyrus-api` and `dennisarq/papyrus-mcp` on Docker Hub.

Search endpoint: `POST https://papyrus.darqlab.net/api/search` with body `{"query":"...","topK":5}`

## MCP Servers

| Instance | URL |
|----------|-----|
| Staging (remote) | `https://mcp-papyrus.darqlab.net/sse` |
| Local dev | `http://localhost:8082/sse` |

Global Claude Code config (`~/.claude.json`) points `papyrus` to the staging SSE URL.
