package io.darqlab.papyrus.ingestor.reingest;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Enumerates {@code document_sources} rows from the <em>live/source</em> database that have
 * an archived extracted-text file — the population {@link ReingestOrchestrator} rebuilds.
 *
 * <p>{@code ArchiveService} has no listing/enumeration method (only lookup by known
 * {@code sourceId} + {@code archiveFilename}), so this queries the source database directly
 * via plain JDBC instead, per the P1.3 task brief.
 */
public class SourceEnumerator {

    /**
     * @param conn open connection to the source/live database
     *             ({@code ingestor.db.source-database})
     */
    public List<SourceRow> enumerate(Connection conn) throws SQLException {
        String sql = """
                SELECT id, filename, content_type, chunking_strategy, chunking_max_tokens,
                       chunking_overlap_tokens, archive_filename, archive_source_id
                FROM document_sources
                WHERE archive_filename IS NOT NULL
                ORDER BY created_at
                """;

        List<SourceRow> rows = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                rows.add(new SourceRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("filename"),
                        rs.getString("content_type"),
                        rs.getString("chunking_strategy"),
                        (Integer) rs.getObject("chunking_max_tokens"),
                        (Integer) rs.getObject("chunking_overlap_tokens"),
                        rs.getString("archive_filename"),
                        rs.getObject("archive_source_id", UUID.class)
                ));
            }
        }
        return rows;
    }
}
