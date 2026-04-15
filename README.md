# Papyrus

Self-hosted document intelligence MCP server. Ingest, index, and semantically search documents — designed for committee meeting minutes, resolutions, and records.

## Features

- Ingest PDFs (digital and scanned), DOCX, XLSX, PPTX, EPUB, HTML, images, and plain text
- OCR via Tesseract with optional Claude Sonnet post-processing to clean up output
- Semantic search powered by Voyage AI embeddings and pgvector (cosine similarity)
- REST API for document management and search
- MCP server (Streamable HTTP transport) for direct integration with AI assistants — works in claude.ai, Claude mobile, and Claude Code
- Web UI for ingestion and search

## Architecture

```
papyrus-core        — Domain layer: records, enums, service interfaces, utilities (no Spring, no I/O)
papyrus-extractor   — Format routing and text extraction (no Spring, no I/O)
papyrus-pipeline    — Spring beans: chunking, embeddings, pgvector storage, job tracking
papyrus-api         — Spring Boot REST API + web UI  (port 8080)
papyrus-mcp         — Spring Boot MCP server, Streamable HTTP transport (port 8080)
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
| `ANTHROPIC_API_KEY` | Only if using Anthropic chat or OCR correction | Anthropic API key |
| `OCR_CORRECTION_ENABLED` | No | Set `true` to enable LLM post-processing of Tesseract output |
| `TESSDATA_PREFIX` | No | Tesseract data path (Alpine Docker: `/usr/share/tessdata`) |
| `CHAT_PROVIDER` | No | Chat LLM provider: `anthropic` (default) or `ollama` |
| `CHAT_MODEL` | No | Model for the selected provider (default: `claude-opus-4-6`) |
| `CHAT_OLLAMA_BASE_URL` | Only if `CHAT_PROVIDER=ollama` | Ollama base URL (default: `http://localhost:11434`) |
| `CHAT_OLLAMA_MODEL` | Only if `CHAT_PROVIDER=ollama` | Ollama model name (default: `llama3.2`) |
| `CHAT_PROMPT_FILE` | No | Path to a custom chat system prompt file — overrides the classpath default |
| `OCR_PROMPT_FILE` | No | Path to a custom OCR correction prompt file — overrides the classpath default |

### Prompt files

The chat system prompt and OCR correction prompt ship as classpath defaults inside the JAR (`prompts/chat-system.md` and `prompts/ocr-correction.md`). To customise without rebuilding:

1. Place your prompt file on the host (e.g. `./volumes/prompts/chat-system.md`)
2. Mount it into the container and set the env var:

```yaml
# docker-compose.yml
environment:
  CHAT_PROMPT_FILE: /etc/papyrus/prompts/chat-system.md
volumes:
  - ./volumes/prompts:/etc/papyrus/prompts:ro
```

3. Restart the container — prompts are loaded once at startup:

```bash
docker compose restart papyrus-api
```

Prompt files must be non-blank and at least 50 characters. The app fails fast at startup if the file is missing or too short.

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
mvn -pl papyrus-core,papyrus-extractor,papyrus-pipeline,papyrus-api,papyrus-mcp install -DskipTests

# Build REST API image
docker build -t papyrus-api:latest .

# Build MCP server image
docker build -f Dockerfile.mcp -t papyrus-mcp:latest .
```

### Local Development (build from source)

Papyrus uses a **two-compose** setup locally: the database runs independently so it is never affected when the app is rebuilt.

**Step 1 — Start the standalone pgvector instance (run once, leave running):**

```bash
docker compose -f /opt/yard/pgvector/docker-compose.yml up -d
```

**Step 2 — Build the JARs and start the app:**

```bash
# Build JARs
mvn -pl papyrus-core,papyrus-extractor,papyrus-pipeline,papyrus-api,papyrus-mcp install -DskipTests

# Start (or rebuild) the app
docker compose -f docker-compose.local.yml up -d --build
```

**Subsequent rebuilds** (app only, DB keeps running):

```bash
mvn -pl papyrus-core,papyrus-extractor,papyrus-pipeline,papyrus-api,papyrus-mcp install -DskipTests
docker compose -f docker-compose.local.yml up -d --build
```

The `docker-compose.local.yml` in the project root builds images directly from source and connects to the external `papyrus-db` Docker network published by the pgvector stack.

### Environment Variables (`.env`)

Create a `.env` file in the project root (see `.env.example`):

```dotenv
VOYAGE_API_KEY=your_voyage_key
ANTHROPIC_API_KEY=your_anthropic_key
POSTGRES_PASSWORD=your_db_password
POSTGRES_DB=papyrus
POSTGRES_USER=papyrus
OCR_CORRECTION_ENABLED=true
API_PORT=8081
MCP_PORT=8082
```

### Production / Staging

The production stack is managed at `/opt/yard/papyrus/docker-compose.yml` and pulls pre-built images from the registry. The pgvector instance runs as a separate service at `/opt/yard/pgvector/docker-compose.yml` publishing the `papyrus-db` network.

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

The MCP server runs separately from the REST API and connects to the same database. It exposes 6 tools via Streamable HTTP transport (MCP spec 2025-03-26):

| Tool | Description |
|---|---|
| `ingest_document` | Ingest a document file (base64-encoded) |
| `ingest_url` | Ingest from a URL |
| `search` | Semantic search over ingested documents |
| `list_sources` | List all ingested sources |
| `get_document` | Retrieve a document by ID |
| `delete_source` | Delete a source and its chunks |

**Endpoint:** `POST /mcp` (single endpoint handles all JSON-RPC messages)

**Claude Code — global (available in all projects):**
```bash
claude mcp add --transport http --scope user papyrus http://localhost:8082/mcp
```

**Claude Code — project scope (team-shared via `.mcp.json`):**
```bash
claude mcp add --transport http --scope project papyrus http://localhost:8082/mcp
```

**claude.ai browser / Claude mobile:**
Go to **Settings → Connectors → Add Custom Connector** and enter the public MCP URL (e.g. `https://mcp-papyrus.example.com/mcp`). Changes sync automatically to the mobile app.

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
| Chat | `/` or `/chat` | AI chat over ingested documents with source citations |
| Ingest | `/ingest` | Upload documents; 3-step image flow with OCR preview |
| Documents | `/documents` | List, filter, and delete ingested documents |

## License

MIT
