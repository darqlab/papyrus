package io.darqlab.papyrus.pipeline.store.repository;

import io.darqlab.papyrus.core.domain.IngestionStatus;
import io.darqlab.papyrus.pipeline.store.entity.DocumentSourceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentSourceRepository extends JpaRepository<DocumentSourceEntity, UUID> {

    List<DocumentSourceEntity> findByStatus(IngestionStatus status);
}
