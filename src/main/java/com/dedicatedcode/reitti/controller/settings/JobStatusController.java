package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.service.jobs.JobInfo;
import com.dedicatedcode.reitti.service.jobs.JobState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Controller
@RequestMapping("/settings")
public class JobStatusController {

    private final boolean dataManagementEnabled;
    private final JobMetadataRepository jobMetadataRepository;

    public JobStatusController(@Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled,
                               JobMetadataRepository jobMetadataRepository) {
        this.dataManagementEnabled = dataManagementEnabled;
        this.jobMetadataRepository = jobMetadataRepository;
    }

    @GetMapping("/job-status")
    public String getJobStatus(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("activeSection", "job-status");
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);

        return "settings/job-status";
    }

    @GetMapping("/queue-stats-content")
    public String getQueueStatsContent(Model model) {
        // Get pending/running jobs
        List<JobMetadataRepository.JobMetadata> pendingJobMetadata = jobMetadataRepository.findByStates(
            List.of(JobState.PREPARING, JobState.AWAITING, JobState.RUNNING)
        );

        // Separate parent jobs from child jobs
        List<JobMetadataRepository.JobMetadata> parentJobs = new ArrayList<>();
        List<JobMetadataRepository.JobMetadata> childJobs = new ArrayList<>();

        for (JobMetadataRepository.JobMetadata metadata : pendingJobMetadata) {
            if (metadata.getParentJobId() == null) {
                parentJobs.add(metadata);
            } else {
                childJobs.add(metadata);
            }
        }

        // Group children by parent
        Map<UUID, List<JobMetadataRepository.JobMetadata>> childrenByParent = new HashMap<>();
        for (JobMetadataRepository.JobMetadata child : childJobs) {
            UUID parentId = child.getParentJobId();
            childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(child);
        }

        // Get past jobs for calculating average runtime
        List<JobMetadataRepository.JobMetadata> pastJobMetadata = jobMetadataRepository.findByStates(
            List.of(JobState.COMPLETED, JobState.FAILED)
        );

        // Calculate average runtime by job type
        Map<String, AverageRuntime> averageRuntimes = calculateAverageRuntimes(pastJobMetadata);

        // Build parent job info with children
        List<JobInfo> pendingJobs = new ArrayList<>();
        for (JobMetadataRepository.JobMetadata parent : parentJobs) {
            JobInfo jobInfo = mapToJobInfo(parent);
            List<JobMetadataRepository.JobMetadata> childrenMetadata = childrenByParent.getOrDefault(parent.getId(), List.of());
            List<JobInfo> children = childrenMetadata.stream()
                .map(this::mapToJobInfo)
                .toList();

            long completedChildren = children.stream()
                .filter(j -> j.state() == JobState.COMPLETED || j.state() == JobState.FAILED)
                .count();

            // Get estimated duration based on job type
            String jobType = parent.getJobType();
            AverageRuntime avgRuntime = averageRuntimes.get(jobType);
            Long estimatedDuration = avgRuntime != null ? avgRuntime.getEstimatedSeconds() : null;

            pendingJobs.add(new JobInfo(
                jobInfo.id(),
                jobInfo.name(),
                jobInfo.description(),
                jobInfo.state(),
                jobInfo.enqueuedAt(),
                jobInfo.scheduledAt(),
                jobInfo.processingAt(),
                jobInfo.finishedAt(),
                jobInfo.canCancel(),
                children,
                completedChildren,
                children.size(),
                estimatedDuration,
                0,
                null
            ));
        }

        // Filter to only top-level parent jobs for past jobs
        List<JobMetadataRepository.JobMetadata> pastParentJobs = pastJobMetadata.stream()
            .filter(job -> job.getParentJobId() == null)
            .toList();

        // Build past job info with duration
        List<JobInfo> pastJobs = new ArrayList<>();
        for (JobMetadataRepository.JobMetadata metadata : pastParentJobs) {
            pastJobs.add(mapToJobInfoWithDuration(metadata));
        }

        // Sort past jobs by finishedAt descending (most recent first)
        pastJobs.sort((a, b) -> {
            Instant aTime = a.finishedAt() != null ? a.finishedAt() : Instant.EPOCH;
            Instant bTime = b.finishedAt() != null ? b.finishedAt() : Instant.EPOCH;
            return bTime.compareTo(aTime);
        });
        model.addAttribute("pendingJobs", pendingJobs);
        model.addAttribute("pastJobs", pastJobs);

        return "settings/job-status :: queue-stats-content";
    }

    private Map<String, AverageRuntime> calculateAverageRuntimes(List<JobMetadataRepository.JobMetadata> jobs) {
        Map<String, List<Long>> durationsByType = new HashMap<>();

        for (JobMetadataRepository.JobMetadata job : jobs) {
            // Only include top-level parent jobs in average calculation
            if (job.getParentJobId() == null && job.getFinishedAt() != null && job.getEnqueuedAt() != null) {
                long durationSeconds = Duration.between(job.getEnqueuedAt(), job.getFinishedAt()).getSeconds();
                if (durationSeconds > 0) {
                    durationsByType.computeIfAbsent(job.getJobType(), k -> new ArrayList<>()).add(durationSeconds);
                }
            }
        }

        Map<String, AverageRuntime> result = new HashMap<>();
        for (Map.Entry<String, List<Long>> entry : durationsByType.entrySet()) {
            List<Long> durations = entry.getValue();
            long sum = durations.stream().mapToLong(Long::longValue).sum();
            long average = sum / durations.size();
            result.put(entry.getKey(), new AverageRuntime(average, durations.size()));
        }

        return result;
    }

    private JobInfo mapToJobInfo(JobMetadataRepository.JobMetadata metadata) {
        JobState state = metadata.getState();
        String jobName = metadata.getFriendlyName();
        String jobDescription = String.format("User ID: %s, Type: %s", metadata.getUserId(), metadata.getJobType());

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
                List.of(),
                0,
                0,
                null,
                ((float) metadata.getMaxProgress() / metadata.getCurrentProgress()) * 100f,
                metadata.getProgressMessage()
        );
    }

    private JobInfo mapToJobInfoWithDuration(JobMetadataRepository.JobMetadata metadata) {
        JobState state = metadata.getState();
        String jobName = metadata.getFriendlyName();
        String jobDescription = String.format("User ID: %s, Type: %s", metadata.getUserId(), metadata.getJobType());

        long durationSeconds = 0;
        if (metadata.getFinishedAt() != null && metadata.getEnqueuedAt() != null) {
            durationSeconds = Duration.between(metadata.getEnqueuedAt(), metadata.getFinishedAt()).getSeconds();
        }

        return new JobInfo(
                metadata.getId(),
                jobName,
                jobDescription,
                state,
                metadata.getEnqueuedAt(),
                metadata.getScheduledAt(),
                metadata.getProcessingAt(),
                metadata.getFinishedAt(),
                false,
                List.of(),
                0,
                0,
                durationSeconds > 0 ? durationSeconds : null,
                ((float) metadata.getMaxProgress() / metadata.getCurrentProgress()) * 100f,
                metadata.getProgressMessage()
        );

    }

    public record AverageRuntime(long averageSeconds, int sampleCount) {
        public String formattedDuration() {
            long hours = averageSeconds / 3600;
            long minutes = (averageSeconds % 3600) / 60;
            long seconds = averageSeconds % 60;

            if (hours > 0) {
                return String.format("%dh %dm %ds", hours, minutes, seconds);
            } else if (minutes > 0) {
                return String.format("%dm %ds", minutes, seconds);
            } else {
                return String.format("%ds", seconds);
            }
        }

        public long getEstimatedSeconds() {
            // Add 20% buffer to the average
            return (long) (averageSeconds * 1.2);
        }

        public String getEstimatedDuration() {
            long estimated = getEstimatedSeconds();
            long hours = estimated / 3600;
            long minutes = (estimated % 3600) / 60;

            if (hours > 0) {
                return String.format("%dh %dm", hours, minutes);
            } else {
                return String.format("%d min", minutes);
            }
        }
    }
}
