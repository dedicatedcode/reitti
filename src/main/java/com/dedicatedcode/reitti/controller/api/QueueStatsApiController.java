package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.service.jobs.JobState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/queue-stats")
public class QueueStatsApiController {

    private final JobMetadataRepository jobMetadataRepository;

    public QueueStatsApiController(JobMetadataRepository jobMetadataRepository) {
        this.jobMetadataRepository = jobMetadataRepository;
    }

    @GetMapping
    public List<JobInfo> getQueueStats() {
        List<JobMetadataRepository.JobMetadata> allMetadata = jobMetadataRepository.findByStates(
                List.of(JobState.PREPARING, JobState.AWAITING, JobState.RUNNING, JobState.COMPLETED, JobState.FAILED)
        );

        return allMetadata.stream()
                .map(this::mapToJobInfo)
                .toList();
    }

    @DeleteMapping("/{id}")
    public void cancelJob(@PathVariable UUID id) {
        jobMetadataRepository.cancel(id);
    }

    private JobInfo mapToJobInfo(JobMetadataRepository.JobMetadata metadata) {
        JobState state = metadata.getState();
        String jobName = metadata.getFriendlyName();
        String jobDescription = String.format("User ID: %s, Type: %s", metadata.getUserId(), metadata.getJobType());

        // Check if job can be cancelled (AWAITING state)
        boolean canCancel = state == JobState.AWAITING;

        return new JobInfo(
                metadata.getId(),
                jobName,
                jobDescription,
                state,
                metadata.getEnqueuedAt(),
                metadata.getScheduledAt(),
                metadata.getProcessingAt(),
                metadata.getFinishedAt(),
                canCancel,
                List.of(),  // children - empty for API responses
                0,          // completedChildren
                0,          // totalChildren
                null        // durationSeconds
        );
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
            boolean canCancel,
            List<JobInfo> children,
            long completedChildren,
            long totalChildren,
            Long durationSeconds
    ) {
        public String getProgressText() {
            if (totalChildren > 0) {
                return completedChildren + " / " + totalChildren + " child jobs";
            }
            return null;
        }

        public int getProgressPercent() {
            if (totalChildren > 0) {
                return (int) ((completedChildren * 100) / totalChildren);
            }
            return 0;
        }

        public String getFormattedDuration() {
            if (durationSeconds == null || durationSeconds == 0) {
                return null;
            }
            long hours = durationSeconds / 3600;
            long minutes = (durationSeconds % 3600) / 60;
            long seconds = durationSeconds % 60;

            if (hours > 0) {
                return String.format("%dh %dm %ds", hours, minutes, seconds);
            } else if (minutes > 0) {
                return String.format("%dm %ds", minutes, seconds);
            } else {
                return String.format("%ds", seconds);
            }
        }
    }
}
