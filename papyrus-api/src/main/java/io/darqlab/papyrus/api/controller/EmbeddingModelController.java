package io.darqlab.papyrus.api.controller;

import io.darqlab.papyrus.api.service.EmbeddingModelInfo;
import io.darqlab.papyrus.api.service.EmbeddingModelInfoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Read-only endpoint backing the {@code /manage} "Active embedding model" display
 * (reingest task P2.1, display half — see {@code EmbeddingModelInfoService} javadoc for
 * what is and isn't in scope).
 */
@RestController
@RequestMapping("/api/embedding-model")
public class EmbeddingModelController {

    private final DataSource dataSource;
    private final EmbeddingModelInfoService infoService;

    public EmbeddingModelController(DataSource dataSource, EmbeddingModelInfoService infoService) {
        this.dataSource = dataSource;
        this.infoService = infoService;
    }

    @GetMapping
    public ResponseEntity<EmbeddingModelInfo> get() {
        return ResponseEntity.ok(infoService.describe(currentDatabaseName()));
    }

    /**
     * The live database name is simply whatever the active datasource is connected to
     * (ADR-009 — no separate runtime pointer). Read it straight off the JDBC connection
     * catalog rather than parsing {@code DATABASE_URL}, so this always reflects the
     * datasource actually in use regardless of how it was configured.
     */
    private String currentDatabaseName() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.getCatalog();
        } catch (SQLException e) {
            return "unknown";
        }
    }
}
