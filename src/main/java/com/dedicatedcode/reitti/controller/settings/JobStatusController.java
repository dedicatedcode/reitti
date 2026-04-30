package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.controller.api.QueueStatsApiController.JobInfo;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
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
import java.util.stream.Collectors;

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

        // Build parent job info with children
        List<JobInfo> pendingJobs = new ArrayList<>();
        for (JobMetadataRepository.JobMetadata parent : parentJobs) {
            JobInfo jobInfo = mapToJobInfo(parent);
            if (jobInfo != null) {
                List<JobMetadataRepository.JobMetadata> childrenMetadata = childrenByParent.getOrDefault(parent.getId(), List.of());
                List<JobInfo> children = childrenMetadata.stream()
                    .map(this::mapToJobInfo)
                    .filter(Objects::nonNull)
                    .toList();

                long completedChildren = children.stream()
                    .filter(j -> j.state() == JobState.COMPLETED || j.state() == JobState.FAILED)
                    .count();

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
                    null
                ));
            }
        }

        // Get past jobs (COMPLETED, FAILED) - only top-level parents
        List<JobMetadataRepository.JobMetadata> pastJobMetadata = jobMetadataRepository.findByStates(
            List.of(JobState.COMPLETED, JobState.FAILED)
        );

        // Filter to only top-level parent jobs
        List<JobMetadataRepository.JobMetadata> pastParentJobs = pastJobMetadata.stream()
            .filter(job -> job.getParentJobId() == null)
            .toList();

        // Build past job info with duration
        List<JobInfo> pastJobs = new ArrayList<>();
        for (JobMetadataRepository.JobMetadata metadata : pastParentJobs) {
            JobInfo jobInfo = mapToJobInfoWithDuration(metadata);
            if (jobInfo != null) {
                pastJobs.add(jobInfo);
            }
        }

        // Sort past jobs by finishedAt descending (most recent first)
        pastJobs.sort((a, b) -> {
            Instant aTime = a.finishedAt() != null ? a.finishedAt() : Instant.EPOCH;
            Instant bTime = b.finishedAt() != null ? b.finishedAt() : Instant.EPOCH;
            return bTime.compareTo(aTime);
        });

        // Calculate average runtime by job type
        Map<String, AverageRuntime> averageRuntimes = calculateAverageRuntimes(pastJobMetadata);

        model.addAttribute("pendingJobs", pendingJobs);
        model.addAttribute("pastJobs", pastJobs);
        model.addAttribute("averageRuntimes", averageRuntimes);

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
        try {
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
                null
            );
        } catch (Exception e) {
            return null;
        }
    }

    private JobInfo mapToJobInfoWithDuration(JobMetadataRepository.JobMetadata metadata) {
        try {
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
                durationSeconds > 0 ? durationSeconds : null
            );
        } catch (Exception e) {
            return null;
        }
    }

    public record AverageRuntime(long averageSeconds, int sampleCount) {
        public String getFormattedDuration() {
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
