# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

## Architecture

Papyrus is a self-hosted document intelligence MCP server used to ingest, index, and semantically search **committee meeting documents** (scanned minutes, resolutions, and records). It has two runnable entry points and four supporting library modules:

```
papyrus-core        — Spring-free domain layer (records, enums, service interfaces, utilities)
papyrus-extractor   — Spring-free format routing and text extraction (no I/O side effects)
papyrus-pipeline    — Spring beans: chunking, Voyage AI embeddings, pgvector storage, job tracking
papyrus-api         — Spring Boot REST API (port 8080/8081 in staging) — main runnable
papyrus-mcp         — Spring Boot MCP server (SSE transport, Spring AI) — second runnable
papyrus-ui          — Static HTML/JS served by papyrus-api
```

### Ingest flow (papyrus-api)

`DocumentController` → `DocumentService` → `FormatRouter` → extractor impl → `OcrCorrectionService` (optional, Claude Sonnet) → `ChunkingService` → `VoyageAiEmbeddingService` → `VectorStoreService` (JDBC + pgvector)

### Search flow

`SearchController` → `VoyageAiEmbeddingService.embed(query)` → `VectorStoreService.searchByVector()` → cosine similarity via pgvector HNSW index

### Format routing (`papyrus-extractor`)

`FormatRouter` dispatches by MIME type to:
- `SmartPdfExtractor` — digital PDF first; falls back to OCR if avg chars/page < 100
- `OcrExtractor` / `ImageOcrExtractor` — Tess4J (Tesseract), 300 DPI
- `OfficeExtractor` (DOCX), `SpreadsheetExtractor` (XLSX), `PresentationExtractor` (PPTX)
- `HtmlExtractor` (Jsoup), `PlainTextExtractor` (TXT/MD/CSV), `EpubExtractor` (ZIP+XHTML)

### Database schema (single Flyway migration: `V1__create_schema.sql`)

- `document_sources` — one row per ingested file (UUID PK, status, metadata)
- `document_chunks` — text chunks with `vector(512)` embeddings, HNSW index on cosine ops
- `ingestion_jobs` — batch job tracking (total/processed/failed counters)

Embeddings are 512-dimensional (`voyage-3-lite`). The vector column type is `vector(512)` — changing embedding providers requires a schema migration.

### MCP server (`papyrus-mcp`)

Runs separately from the REST API. Tools defined in `IngestTools` and `SearchTools` using Spring AI `@Tool` annotations. Both runnables share the same `papyrus-pipeline` and `papyrus-extractor` modules and connect to the same PostgreSQL database.

## Key Configuration

| Env var | Purpose |
|---|---|
| `VOYAGE_API_KEY` | Required for embeddings |
| `DATABASE_URL` | PostgreSQL JDBC URL (default: `jdbc:postgresql://localhost:5432/papyrus`) |
| `OCR_CORRECTION_ENABLED` | Set `true` to enable Claude LLM post-processing of Tesseract output |
| `ANTHROPIC_API_KEY` | Required when OCR correction is enabled |
| `TESSDATA_PREFIX` | Tesseract data path (Alpine Docker: `/usr/share/tessdata`) |

## Staging Deployment

Running containers (managed externally at `/opt/yard/papyrus/docker-compose.yml`):
- `papyrus-api` → `localhost:8081`
- `papyrus-mcp` → `localhost:8082`
- `papyrus-pgvector` (pgvector/pgvector:pg16)

Search endpoint: `POST http://localhost:8081/api/search` with body `{"query":"...","topK":5}`
