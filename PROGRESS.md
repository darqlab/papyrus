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

## Upcoming

| Phase | Scope | Status |
|-------|-------|--------|
| 2 | OCR fallback (Tess4J, scanned PDF detection) | Planned |
| 3 | Chunking + Voyage AI embeddings + pgvector | Planned |
| 4 | MCP server (`ingest_document` + `search`) | Planned |
| 5 | REST API + web dashboard | Planned |
| 6 | Batch ingestion, URL ingest, remaining MCP tools | Planned |
| 7 | Ollama embedding provider | Planned |
| 8 | XLSX, PPTX, EPUB, multi-language OCR | Planned |
