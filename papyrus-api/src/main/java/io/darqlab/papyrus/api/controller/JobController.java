package io.darqlab.papyrus.api.controller;

import io.darqlab.papyrus.api.controller.dto.JobResponse;
import io.darqlab.papyrus.pipeline.job.IngestionJobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final IngestionJobService jobService;

    public JobController(IngestionJobService jobService) {
        this.jobService = jobService;
    }

    /**
     * Get ingestion job status.
     * <p>GET /api/jobs/{id}</p>
     */
    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> get(@PathVariable UUID id) {
        return jobService.find(id)
                .map(j -> ResponseEntity.ok(new JobResponse(
                        j.id(), j.status(), j.total(), j.processed(), j.failed())))
                .orElse(ResponseEntity.notFound().build());
    }
}
