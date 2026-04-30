package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.service.jobs.JobState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/queue-stats")
public class QueueStatsApiController {

    private final JobMetadataRepository jobMetadataRepository;

    public QueueStatsApiController(JobMetadataRepository jobMetadataRepository) {
        this.jobMetadataRepository = jobMetadataRepository;
    }


    @DeleteMapping("/{jobId}")
    public ResponseEntity<Void> cancelJob(@PathVariable UUID jobId) {
        // Check if it's an ImportJob in waiting state
        Optional<JobState> importJobState = jobMetadataRepository.getState(jobId);

        if (importJobState.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        if (importJobState.get() != JobState.AWAITING) {
            return ResponseEntity.badRequest().build();
        }

        // Cancel the job
        try {
//            storageProvider.deletePermanently(jobId);
            jobMetadataRepository.updateState(jobId, JobState.CANCELLED, Instant.now());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    public record JobInfo(
            UUID id,
            String name,
            String description,
            JobState state,
            Instant enqueuedAt,
            Instant scheduledAt,
            Instant processingAt,
            Instant finishedAt,
            boolean canCancel
    ) {}
}