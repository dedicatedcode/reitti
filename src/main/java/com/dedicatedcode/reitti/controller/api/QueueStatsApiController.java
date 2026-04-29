package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.repository.ImportJobRepository;
import com.dedicatedcode.reitti.service.jobs.JobState;
import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.navigation.OffsetBasedPageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/queue-stats")
public class QueueStatsApiController {

    private final StorageProvider storageProvider;
    private final ImportJobRepository importJobRepository;
    private final OffsetBasedPageRequest amountRequest = new OffsetBasedPageRequest("updatedAt:ASC", 0, 100);

    public QueueStatsApiController(StorageProvider storageProvider,
                                   ImportJobRepository importJobRepository) {
        this.storageProvider = storageProvider;
        this.importJobRepository = importJobRepository;
    }


    @DeleteMapping("/{jobId}")
    public ResponseEntity<Void> cancelJob(@PathVariable UUID jobId) {
        // Check if it's an ImportJob in waiting state
        Optional<JobState> importJobState = importJobRepository.getState(jobId);

        if (importJobState.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        if (importJobState.get() != JobState.AWAITING) {
            return ResponseEntity.badRequest().build();
        }

        // Cancel the job
        try {
            storageProvider.deletePermanently(jobId);
            importJobRepository.updateState(jobId, JobState.CANCELLED);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    public record JobInfo(
            UUID id,
            String name,
            String description,
            String state,
            Instant enqueuedAt,
            Instant scheduledAt,
            Instant processingAt,
            Instant finishedAt,
            boolean canCancel
    ) {}
}