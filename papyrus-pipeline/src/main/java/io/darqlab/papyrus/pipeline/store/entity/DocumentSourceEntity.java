package io.darqlab.papyrus.pipeline.store.entity;

import io.darqlab.papyrus.core.domain.Document;
import io.darqlab.papyrus.core.domain.IngestionStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "document_sources")
public class DocumentSourceEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private String filename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "archive_filename")
    private String archiveFilename;

    @Column(name = "archive_source_id")
    private UUID archiveSourceId;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column
    private String language = "eng";

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private IngestionStatus status;

    @Column
    private String error;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected DocumentSourceEntity() {}

    public DocumentSourceEntity(UUID id, String filename, String contentType,
                                Long fileSize, String language, IngestionStatus status) {
        this.id = id;
        this.filename = filename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.language = language;
        this.status = status;
    }

    public Document toDomain() {
        return new Document(id, filename, contentType, fileSize, pageCount,
                language, status, error, Map.of(), createdAt, updatedAt);
    }

    // ── Getters/setters ──────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public String getFilename() { return filename; }
    public String getContentType() { return contentType; }
    public String getArchiveFilename() { return archiveFilename; }
    public void setArchiveFilename(String archiveFilename) { this.archiveFilename = archiveFilename; }
    public UUID getArchiveSourceId() { return archiveSourceId; }
    public void setArchiveSourceId(UUID archiveSourceId) { this.archiveSourceId = archiveSourceId; }
    public IngestionStatus getStatus() { return status; }
    public void setStatus(IngestionStatus status) { this.status = status; }
    public void setError(String error) { this.error = error; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
    public Instant getCreatedAt() { return createdAt; }
}
