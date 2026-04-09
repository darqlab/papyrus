package io.darqlab.papyrus.pipeline.store.repository;

import io.darqlab.papyrus.pipeline.store.entity.IngestionJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IngestionJobRepository extends JpaRepository<IngestionJobEntity, UUID> {}
