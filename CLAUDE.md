# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> Global preferences: see /home/dennis/CLAUDE.md

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

Two compose files in the project root:

```bash
# 1. Start the standalone DB (once — survives app rebuilds)
docker compose -f docker-compose.db.yml up -d

# 2. Build and start API + MCP
docker compose up -d --build

# 3. Rebuild just the API after a Maven build
mvn -pl papyrus-core,papyrus-extractor,papyrus-pipeline,papyrus-api install -DskipTests
docker compose up -d --build papyrus-api

# Stop app (DB keeps running)
docker compose down
```

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
papyrus-pipeline    — Spring beans: chunking, Voyage AI embeddings, pgvector storage, job tracking, archive
papyrus-api         — Spring Boot REST API (port 8080/8081 in staging) — main runnable
papyrus-mcp         — Spring Boot MCP server (SSE transport, Spring AI) — second runnable
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

### MCP server (`papyrus-mcp`)

Runs separately from the REST API. Tools defined in `IngestTools` and `SearchTools` using Spring AI `@Tool` annotations. Both runnables share the same `papyrus-pipeline` and `papyrus-extractor` modules and connect to the same PostgreSQL database.

## Key Configuration

| Env var | Purpose |
|---------|---------|
| `VOYAGE_API_KEY` | Required — Voyage AI embeddings |
| `ANTHROPIC_API_KEY` | Required for OCR correction and chat |
| `DATABASE_URL` | PostgreSQL JDBC URL (default: `jdbc:postgresql://localhost:5432/papyrus`) |
| `OCR_CORRECTION_ENABLED` | `true` to enable Claude LLM post-processing of Tesseract output |
| `ARCHIVE_ENABLED` | `true` to save originals + extracted text to filesystem |
| `ARCHIVE_PATH` | Archive root directory (default: `/data/papyrus/archive`) |
| `TESSDATA_PREFIX` | Tesseract data path (Alpine Docker: `/usr/share/tessdata`) |

## Web UI Pages

| URL | File | Purpose |
|-----|------|---------|
| `/chat` | `chat.html` | RAG chat with source citations and PDF export |
| `/ingest` | `ingest.html` | Upload files; OCR preview/verify flow for images |
| `/documents` | `documents.html` | List, filter, delete ingested documents |
| `/manage` | `manage.html` | Archive manager — re-ingest with current embedding model |

## Staging Deployment

Running stack managed at `/opt/yard/papyrus/` (separate from this repo):
- `papyrus-api` → `localhost:8081`
- `papyrus-mcp` → `localhost:8082`
- `papyrus-pgvector` (pgvector/pgvector:pg16)

Production images: `dennisarq/papyrus-api` and `dennisarq/papyrus-mcp` on Docker Hub.

Search endpoint: `POST http://localhost:8081/api/search` with body `{"query":"...","topK":5}`
