package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.service.jobs.JobInfo;
import com.dedicatedcode.reitti.service.jobs.JobState;
import com.dedicatedcode.reitti.service.jobs.JobType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    public String getQueueStatsContent(@RequestParam(defaultValue = "UTC") ZoneId timezone, Model model) {
        // Fetch all non-SSE jobs in active states
        List<JobMetadataRepository.JobMetadata> activeJobs = filterNonSSE(
                jobMetadataRepository.findByStates(
                        List.of(JobState.PREPARING, JobState.AWAITING, JobState.RUNNING)
                )
        );
        // Fetch all non-SSE jobs in terminal states (completed/failed)
        List<JobMetadataRepository.JobMetadata> terminalJobs = filterNonSSE(
                jobMetadataRepository.findByStates(
                        List.of(JobState.COMPLETED, JobState.FAILED)
                )
        );

        // Combine to get full picture of parents and children
        List<JobMetadataRepository.JobMetadata> allJobs = new ArrayList<>(activeJobs);
        allJobs.addAll(terminalJobs);

        // Separate parent and child jobs
        Map<Boolean, List<JobMetadataRepository.JobMetadata>> partitioned = allJobs.stream()
                .collect(Collectors.partitioningBy(job -> job.getParentJobId() == null));
        List<JobMetadataRepository.JobMetadata> parentJobs = partitioned.get(true);
        List<JobMetadataRepository.JobMetadata> childJobs = partitioned.get(false);

        // Group children by parent ID
        Map<UUID, List<JobMetadataRepository.JobMetadata>> childrenByParent = childJobs.stream()
                .collect(Collectors.groupingBy(JobMetadataRepository.JobMetadata::getParentJobId));

        // Separate parent jobs into pending and fully complete (past)
        List<JobMetadataRepository.JobMetadata> pendingParents = new ArrayList<>();
        List<JobMetadataRepository.JobMetadata> pastParents = new ArrayList<>();

        for (JobMetadataRepository.JobMetadata parent : parentJobs) {
            List<JobMetadataRepository.JobMetadata> children = childrenByParent.getOrDefault(parent.getId(), List.of());
            boolean hasActiveChildren = children.stream()
                    .anyMatch(child -> !isTerminal(child.getState()));

            if (!isTerminal(parent.getState()) || hasActiveChildren) {
                pendingParents.add(parent);
            } else {
                pastParents.add(parent);
            }
        }

        // Build pending job info (with children details)
        Map<JobType, AverageRuntime> averageRuntimes = calculateAverageRuntimes(pastParents);
        List<JobInfo> pendingJobs = pendingParents.stream()
                .map(parent -> buildPendingJobInfo(timezone, parent, childrenByParent, averageRuntimes))
                .collect(Collectors.toList());

        // Build past job info (with duration)
        List<JobInfo> pastJobs = pastParents.stream()
                .map(j -> mapToJobInfo(timezone, j))
                .sorted(Comparator.comparing(JobInfo::finishedAt).reversed())
                .collect(Collectors.toList());

        model.addAttribute("pendingJobs", pendingJobs);
        model.addAttribute("pastJobs", pastJobs);
        return "settings/job-status :: queue-stats-content";
    }

    private boolean isTerminal(JobState state) {
        return state == JobState.COMPLETED || state == JobState.FAILED;
    }

    private List<JobMetadataRepository.JobMetadata> filterNonSSE(List<JobMetadataRepository.JobMetadata> jobs) {
        return jobs.stream()
                .filter(job -> job.getJobType() != JobType.SSE_EVENT)
                .toList();
    }

    private JobInfo buildPendingJobInfo(ZoneId timezone, JobMetadataRepository.JobMetadata parent,
                                        Map<UUID, List<JobMetadataRepository.JobMetadata>> childrenByParent,
                                        Map<JobType, AverageRuntime> averageRuntimes) {
        List<JobMetadataRepository.JobMetadata> childrenMeta = childrenByParent.getOrDefault(parent.getId(), List.of());
        List<JobInfo> children = childrenMeta.stream()
                .map(j -> mapToJobInfo(timezone, j))
                .toList();
        long completedChildren = children.stream()
                .filter(j -> isTerminal(j.state()))
                .count();
        long totalChildren = children.size();

        // Basic info from the parent itself
        JobInfo base = mapToJobInfo(timezone, parent);

        // Average runtime estimate
        AverageRuntime avgRuntime = averageRuntimes.get(parent.getJobType());
        Long estimatedDuration = avgRuntime != null ? avgRuntime.getEstimatedSeconds() : null;

        return new JobInfo(
                base.id(),
                base.name(),
                base.description(),
                base.state(),
                base.enqueuedAt(),
                base.scheduledAt(),
                base.processingAt(),
                base.finishedAt(),
                base.canCancel(),
                children,
                completedChildren,
                totalChildren,
                estimatedDuration,
                0,  // no progress for parent grouping
                null
        );
    }

    private Map<JobType, AverageRuntime> calculateAverageRuntimes(List<JobMetadataRepository.JobMetadata> fullyCompleteParents) {
        Map<JobType, List<Long>> durationsByType = new HashMap<>();
        for (JobMetadataRepository.JobMetadata job : fullyCompleteParents) {
            if (job.getFinishedAt() != null && job.getEnqueuedAt() != null) {
                long durationSeconds = Duration.between(job.getEnqueuedAt(), job.getFinishedAt()).getSeconds();
                if (durationSeconds > 0) {
                    durationsByType.computeIfAbsent(job.getJobType(), k -> new ArrayList<>()).add(durationSeconds);
                }
            }
        }
        Map<JobType, AverageRuntime> result = new HashMap<>();
        for (Map.Entry<JobType, List<Long>> entry : durationsByType.entrySet()) {
            List<Long> durations = entry.getValue();
            if (!durations.isEmpty()) {
                long average = durations.stream().mapToLong(Long::longValue).sum() / durations.size();
                result.put(entry.getKey(), new AverageRuntime(average, durations.size()));
            }
        }
        return result;
    }

    private JobInfo mapToJobInfo(ZoneId timezone, JobMetadataRepository.JobMetadata metadata) {
        JobState state = metadata.getState();
        String jobName = metadata.getFriendlyName();
        String jobDescription = String.format("User ID: %s, Type: %s", metadata.getUserId(), metadata.getJobType());
        boolean canCancel = state == JobState.AWAITING;

        return new JobInfo(
                metadata.getId(),
                jobName,
                jobDescription,
                state,
                toLocalDateTime(metadata.getEnqueuedAt(), timezone),
                toLocalDateTime(metadata.getScheduledAt(), timezone),
                toLocalDateTime(metadata.getProcessingAt(), timezone),
                toLocalDateTime(metadata.getFinishedAt(), timezone),
                canCancel,
                List.of(),
                0,
                0,
                null,
                progressPercent(metadata),
                metadata.getProgressMessage()
        );
    }

    private LocalDateTime toLocalDateTime(Instant instant, ZoneId timezone) {
        if (instant == null || timezone == null) {
            return null;
        } else {
            return instant.atZone(timezone).toLocalDateTime();
        }
    }
    private float progressPercent(JobMetadataRepository.JobMetadata metadata) {
        if (metadata.getMaxProgress() == null || metadata.getCurrentProgress() == null || metadata.getMaxProgress() == 0) return 0f;
        return ((float) metadata.getCurrentProgress() / metadata.getMaxProgress()) * 100f;
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