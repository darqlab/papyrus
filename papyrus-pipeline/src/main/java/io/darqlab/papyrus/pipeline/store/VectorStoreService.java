package io.darqlab.papyrus.pipeline.store;

import io.darqlab.papyrus.core.domain.DocumentChunk;
import io.darqlab.papyrus.core.domain.SearchResult;
import io.darqlab.papyrus.core.domain.Source;
import io.darqlab.papyrus.pipeline.store.entity.DocumentSourceEntity;
import io.darqlab.papyrus.pipeline.store.repository.DocumentSourceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class VectorStoreService {

    private final JdbcTemplate jdbc;
    private final DocumentSourceRepository sourceRepository;

    public VectorStoreService(JdbcTemplate jdbc, DocumentSourceRepository sourceRepository) {
        this.jdbc             = jdbc;
        this.sourceRepository = sourceRepository;
    }

    /**
     * Persist a batch of chunks with their embeddings for a given source.
     *
     * @param sourceId   the parent document source UUID
     * @param contents   chunk text in order
     * @param embeddings one embedding vector per chunk (must match contents size)
     * @param tokenCounts estimated token count per chunk
     * @param pageNumbers page number per chunk (nullable)
     */
    @Transactional
    public void storeChunks(UUID sourceId, List<String> contents,
                            List<List<Float>> embeddings,
                            List<Integer> tokenCounts,
                            List<Integer> pageNumbers) {
        for (int i = 0; i < contents.size(); i++) {
            jdbc.update("""
                    INSERT INTO document_chunks
                        (id, source_id, chunk_index, page_number, content, embedding, token_count)
                    VALUES (?, ?, ?, ?, ?, ?::vector, ?)
                    """,
                    UUID.randomUUID(),
                    sourceId,
                    i,
                    pageNumbers != null ? pageNumbers.get(i) : null,
                    contents.get(i),
                    toVectorString(embeddings.get(i)),
                    tokenCounts.get(i)
            );
        }
    }

    /**
     * Semantic search using pgvector cosine distance.
     *
     * @param queryVector the embedded query
     * @param topK        maximum results
     * @param sourceId    optional — restrict search to one source; null searches all
     */
    public List<SearchResult> searchByVector(List<Float> queryVector, int topK, UUID sourceId) {
        String vectorStr = toVectorString(queryVector);

        if (sourceId != null) {
            return jdbc.query("""
                    SELECT dc.id, dc.source_id, dc.chunk_index, dc.page_number,
                           dc.content, dc.token_count, dc.created_at,
                           ds.filename, ds.archive_filename,
                           1 - (dc.embedding <=> ?::vector) AS score
                    FROM document_chunks dc
                    JOIN document_sources ds ON dc.source_id = ds.id
                    WHERE dc.embedding IS NOT NULL
                      AND dc.source_id = ?
                    ORDER BY dc.embedding <=> ?::vector
                    LIMIT ?
                    """,
                    this::mapRow,
                    vectorStr, sourceId, vectorStr, topK);
        }

        return jdbc.query("""
                SELECT dc.id, dc.source_id, dc.chunk_index, dc.page_number,
                       dc.content, dc.token_count, dc.created_at,
                       ds.filename, ds.archive_filename,
                       1 - (dc.embedding <=> ?::vector) AS score
                FROM document_chunks dc
                JOIN document_sources ds ON dc.source_id = ds.id
                WHERE dc.embedding IS NOT NULL
                ORDER BY dc.embedding <=> ?::vector
                LIMIT ?
                """,
                this::mapRow,
                vectorStr, vectorStr, topK);
    }

    /**
     * Delete all chunks for a source, then delete the source record itself.
     */
    @Transactional
    public void deleteBySourceId(UUID sourceId) {
        jdbc.update("DELETE FROM document_chunks WHERE source_id = ?", sourceId);
        sourceRepository.deleteById(sourceId);
    }

    /**
     * List all document sources as summary views.
     */
    public List<Source> listSources(int limit, int offset) {
        return sourceRepository.findAll().stream()
                .sorted(Comparator.comparing(DocumentSourceEntity::getCreatedAt,
                        Comparator.reverseOrder()))
                .skip(offset)
                .limit(limit)
                .map(this::toSource)
                .toList();
    }

    /**
     * Find a single source by ID, or null if not found.
     */
    public Source findById(UUID id) {
        return sourceRepository.findById(id).map(this::toSource).orElse(null);
    }

    private Source toSource(DocumentSourceEntity e) {
        String strategy = e.getChunkingStrategy() != null ? e.getChunkingStrategy().name() : null;
        return new Source(e.getId(), e.getFilename(), e.getArchiveFilename(),
                e.getArchiveSourceId(), e.getContentType(), e.getStatus(), e.getCreatedAt(),
                strategy, e.getChunkingMaxTokens(), e.getChunkingOverlapTokens());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String toVectorString(List<Float> embedding) {
        return embedding.stream()
                .map(Object::toString)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private SearchResult mapRow(ResultSet rs, int rowNum) throws SQLException {
        DocumentChunk chunk = new DocumentChunk(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("source_id")),
                rs.getInt("chunk_index"),
                rs.getObject("page_number", Integer.class),
                rs.getString("content"),
                rs.getObject("token_count", Integer.class),
                null,
                rs.getTimestamp("created_at").toInstant()
        );
        return new SearchResult(chunk, rs.getDouble("score"), rs.getString("filename"), rs.getString("archive_filename"));
    }
}
