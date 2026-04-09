package io.darqlab.papyrus.pipeline.store.entity;

import io.darqlab.papyrus.core.domain.IngestionJob;
import io.darqlab.papyrus.core.domain.JobStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ingestion_jobs")
public class IngestionJobEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private JobStatus status;

    @Column
    private Integer total;

    @Column
    private int processed = 0;

    @Column
    private int failed = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected IngestionJobEntity() {}

    public IngestionJobEntity(UUID id, JobStatus status, Integer total) {
        this.id = id;
        this.status = status;
        this.total = total;
    }

    public IngestionJob toDomain() {
        return new IngestionJob(id, status, total, processed, failed, createdAt, updatedAt);
    }

    // ── Getters/setters ──────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }
    public int getProcessed() { return processed; }
    public void setProcessed(int processed) { this.processed = processed; }
    public int getFailed() { return failed; }
    public void setFailed(int failed) { this.failed = failed; }
}
