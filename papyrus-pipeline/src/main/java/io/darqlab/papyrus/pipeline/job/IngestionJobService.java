package io.darqlab.papyrus.pipeline.job;

import io.darqlab.papyrus.core.domain.IngestionJob;
import io.darqlab.papyrus.core.domain.JobStatus;
import io.darqlab.papyrus.pipeline.store.entity.IngestionJobEntity;
import io.darqlab.papyrus.pipeline.store.repository.IngestionJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class IngestionJobService {

    private final IngestionJobRepository repository;

    public IngestionJobService(IngestionJobRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public IngestionJob createJob(int total) {
        IngestionJobEntity entity = new IngestionJobEntity(UUID.randomUUID(), JobStatus.QUEUED, total);
        return repository.save(entity).toDomain();
    }

    @Transactional
    public void markRunning(UUID jobId) {
        update(jobId, entity -> entity.setStatus(JobStatus.RUNNING));
    }

    @Transactional
    public void recordSuccess(UUID jobId) {
        update(jobId, entity -> entity.setProcessed(entity.getProcessed() + 1));
    }

    @Transactional
    public void recordFailure(UUID jobId) {
        update(jobId, entity -> {
            entity.setFailed(entity.getFailed() + 1);
            entity.setProcessed(entity.getProcessed() + 1);
        });
    }

    @Transactional
    public void markDone(UUID jobId) {
        update(jobId, entity -> entity.setStatus(JobStatus.DONE));
    }

    @Transactional
    public void markFailed(UUID jobId) {
        update(jobId, entity -> entity.setStatus(JobStatus.FAILED));
    }

    public Optional<IngestionJob> find(UUID jobId) {
        return repository.findById(jobId).map(IngestionJobEntity::toDomain);
    }

    private void update(UUID jobId, java.util.function.Consumer<IngestionJobEntity> updater) {
        repository.findById(jobId).ifPresent(entity -> {
            updater.accept(entity);
            repository.save(entity);
        });
    }
}
