# Papyrus

Self-hosted document intelligence MCP server. Ingest, index, and semantically search documents — designed for committee meeting minutes, resolutions, and records.

## Features

- Ingest PDFs (digital and scanned), DOCX, XLSX, PPTX, EPUB, HTML, images, and plain text
- OCR via Tesseract with optional Claude Sonnet post-processing to clean up output
- Semantic search powered by Voyage AI embeddings and pgvector (cosine similarity)
- REST API for document management and search
- MCP server (SSE transport) for direct integration with AI assistants
- Web UI for ingestion and search

## Architecture

```
papyrus-core        — Domain layer: records, enums, service interfaces, utilities (no Spring, no I/O)
papyrus-extractor   — Format routing and text extraction (no Spring, no I/O)
papyrus-pipeline    — Spring beans: chunking, embeddings, pgvector storage, job tracking
papyrus-api         — Spring Boot REST API + web UI  (port 8080)
papyrus-mcp         — Spring Boot MCP server, SSE transport (port 8080)
papyrus-ui          — Static HTML/JS served by papyrus-api
```

### Ingest flow

```
DocumentController → DocumentService → FormatRouter → extractor
    → OcrCorrectionService (optional) → ChunkingService
    → VoyageAiEmbeddingService → VectorStoreService (pgvector)
```

### Search flow

```
SearchController → VoyageAiEmbeddingService.embed(query)
    → VectorStoreService.searchByVector() → cosine similarity (HNSW index)
```

### Format support

| Extractor | Formats |
|---|---|
| `SmartPdfExtractor` | PDF — digital first, OCR fallback if < 100 chars/page |
| `OcrExtractor` | PDF (scanned) — Tess4J at 300 DPI |
| `ImageOcrExtractor` | PNG, JPG, TIFF, BMP, GIF |
| `OfficeExtractor` | DOCX |
| `SpreadsheetExtractor` | XLSX |
| `PresentationExtractor` | PPTX |
| `EpubExtractor` | EPUB |
| `HtmlExtractor` | HTML |
| `PlainTextExtractor` | TXT, MD, CSV |

## Prerequisites

- Java 21
- Maven 3.6+
- Docker (for pgvector and integration tests)
- [Voyage AI API key](https://www.voyageai.com/) — for embeddings (`voyage-3-lite`, 512 dimensions)
- Anthropic API key — optional, for OCR correction only

## Configuration

| Environment variable | Required | Description |
|---|---|---|
| `VOYAGE_API_KEY` | Yes | Voyage AI embeddings |
| `DATABASE_URL` | Yes | PostgreSQL JDBC URL (default: `jdbc:postgresql://localhost:5432/papyrus`) |
| `ANTHROPIC_API_KEY` | Only if OCR correction enabled | Claude Sonnet API key |
| `OCR_CORRECTION_ENABLED` | No | Set `true` to enable LLM post-processing of Tesseract output |
| `TESSDATA_PREFIX` | No | Tesseract data path (Alpine Docker: `/usr/share/tessdata`) |

## Build

```bash
# Build all modules (skip tests)
mvn -pl papyrus-core,papyrus-extractor,papyrus-pipeline,papyrus-api,papyrus-mcp install -DskipTests

# Full build with tests (requires Docker for Testcontainers)
mvn verify

# Build a specific module
mvn -pl papyrus-core test
```

## Docker

```bash
# Build API fat JAR first
mvn -pl papyrus-core,papyrus-extractor,papyrus-pipeline,papyrus-api install -DskipTests

# Build REST API image
docker build -t papyrus-api:latest .

# Build MCP server image
docker build -f Dockerfile.mcp -t papyrus-mcp:latest .
```

### Docker Compose (recommended)

```yaml
services:
  papyrus-db:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_DB: papyrus
      POSTGRES_USER: papyrus
      POSTGRES_PASSWORD: papyrus

  papyrus-api:
    image: papyrus-api:latest
    ports:
      - "8081:8080"
    environment:
      DATABASE_URL: jdbc:postgresql://papyrus-db:5432/papyrus
      VOYAGE_API_KEY: ${VOYAGE_API_KEY}
      ANTHROPIC_API_KEY: ${ANTHROPIC_API_KEY}
      OCR_CORRECTION_ENABLED: "false"
      TESSDATA_PREFIX: /usr/share/tessdata
    depends_on:
      - papyrus-db

  papyrus-mcp:
    image: papyrus-mcp:latest
    ports:
      - "8082:8080"
    environment:
      DATABASE_URL: jdbc:postgresql://papyrus-db:5432/papyrus
      VOYAGE_API_KEY: ${VOYAGE_API_KEY}
    depends_on:
      - papyrus-db
```

## REST API

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/documents` | Ingest a file (multipart) |
| `POST` | `/api/documents/url` | Ingest from a URL |
| `POST` | `/api/documents/batch` | Batch ingest (multipart, multiple files) |
| `POST` | `/api/documents/preview` | Extract text without storing |
| `GET` | `/api/documents` | List all sources |
| `GET` | `/api/documents/{id}` | Get source by ID |
| `DELETE` | `/api/documents/{id}` | Delete source and its chunks |
| `POST` | `/api/search` | Semantic search |
| `GET` | `/api/jobs/{id}` | Batch job status |

**Search request:**
```json
{ "query": "budget approval resolution", "topK": 5 }
```

## MCP Server

The MCP server runs separately from the REST API and connects to the same database. It exposes 6 tools via SSE transport:

| Tool | Description |
|---|---|
| `ingest_document` | Ingest a document file |
| `ingest_url` | Ingest from a URL |
| `search` | Semantic search over ingested documents |
| `list_sources` | List all ingested sources |
| `get_document` | Retrieve a document by ID |
| `delete_source` | Delete a source and its chunks |

**Claude Desktop / Claude Code config:**
```json
{
  "mcpServers": {
    "papyrus": {
      "url": "http://localhost:8082/sse"
    }
  }
}
```

## Database Schema

Managed by Flyway (`V1__create_schema.sql`):

- `document_sources` — one row per ingested file (UUID PK, status, metadata)
- `document_chunks` — text chunks with `vector(512)` embeddings, HNSW index on cosine ops
- `ingestion_jobs` — batch job tracking

Embeddings are 512-dimensional (`voyage-3-lite`). Changing embedding providers requires a schema migration to update the vector column type.

## Web UI

Served by `papyrus-api` at `/`:

| Page | URL | Description |
|---|---|---|
| Ingest | `/ingest` | Upload documents; 3-step image flow with OCR preview |
| Search | `/search` | Semantic search with source filter and relevance scores |

## License

MIT
