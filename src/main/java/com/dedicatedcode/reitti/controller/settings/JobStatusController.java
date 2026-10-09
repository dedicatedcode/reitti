package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.I18nService;
import com.dedicatedcode.reitti.service.jobs.JobInfo;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.jobs.JobState;
import com.dedicatedcode.reitti.service.jobs.JobType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
@RequestMapping("/settings")
public class JobStatusController {

    private static final List<JobState> ACTIVE_STATES = List.of(JobState.PREPARING, JobState.CREATED, JobState.AWAITING, JobState.RUNNING);
    private static final List<JobState> TERMINAL_STATES = List.of(JobState.COMPLETED, JobState.FAILED);
    private static final List<JobState> ALL_STATES = Stream.of(ACTIVE_STATES, TERMINAL_STATES).flatMap(List::stream).toList();

    private final boolean dataManagementEnabled;
    private final JobMetadataRepository jobMetadataRepository;
    private final JobSchedulingService jobSchedulingService;
    private final UserJdbcService userJdbcService;
    private final I18nService i18n;

    public JobStatusController(@Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled,
                               JobMetadataRepository jobMetadataRepository,
                               JobSchedulingService jobSchedulingService,
                               UserJdbcService userJdbcService,
                               I18nService i18n) {
        this.dataManagementEnabled = dataManagementEnabled;
        this.jobMetadataRepository = jobMetadataRepository;
        this.jobSchedulingService = jobSchedulingService;
        this.userJdbcService = userJdbcService;
        this.i18n = i18n;
    }

    @GetMapping("/job-status")
    public String getJobStatus(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("activeSection", "job-status");
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);
        return "settings/job-status";
    }

    @GetMapping("/queue-stats-content")
    public String getQueueStatsContent(@AuthenticationPrincipal User user,
                                       @RequestParam(defaultValue = "UTC") ZoneId timezone,
                                       Model model) {
        boolean isAdmin = user.getRole() == Role.ADMIN;

        List<JobMetadataRepository.JobMetadata> activeParents =
                jobMetadataRepository.findParentJobsByStates(ACTIVE_STATES, user.getId(), isAdmin);

        List<JobMetadataRepository.JobMetadata> terminalParents =
                jobMetadataRepository.findParentJobsByStates(TERMINAL_STATES, user.getId(), isAdmin);

        // Children are fetched by parent id instead of by user, so a child never disappears from the
        // progress of a parent the current user is allowed to see.
        Map<UUID, List<JobMetadataRepository.JobMetadata>> childrenByParent = jobMetadataRepository
                .findByParentJobIds(ALL_STATES, parentIds(activeParents, terminalParents)).stream()
                .collect(Collectors.groupingBy(JobMetadataRepository.JobMetadata::getParentJobId));

        List<JobMetadataRepository.JobMetadata> allParents = new ArrayList<>(activeParents);
        allParents.addAll(terminalParents);

        // Separate parent jobs into pending and fully complete (past)
        List<JobMetadataRepository.JobMetadata> pendingParents = new ArrayList<>();
        List<JobMetadataRepository.JobMetadata> pastParents = new ArrayList<>();

        for (JobMetadataRepository.JobMetadata parent : allParents) {
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
                .sorted(Comparator.comparing(JobInfo::state).thenComparing(JobInfo::enqueuedAt))
                .collect(Collectors.toList());

        // Build past job info (with duration)
        List<JobInfo> pastJobs = pastParents.stream()
                .map(j -> mapToJobInfo(timezone, j))
                .sorted(Comparator.comparing(JobInfo::finishedAt).reversed())
                .limit(25)
                .collect(Collectors.toList());

        model.addAttribute("pendingJobs", pendingJobs);
        model.addAttribute("pastJobs", pastJobs);
        return "settings/job-status :: queue-stats-content";
    }

    @DeleteMapping("/job/{id}")
    public String cancelJob(@AuthenticationPrincipal User user,
                            @PathVariable UUID id,
                            @RequestParam(defaultValue = "UTC") ZoneId timezone,
                            Model model) {
        jobMetadataRepository.findById(id)
                .ifPresent(metadata -> {
                    if (!isVisibleTo(metadata, user)) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
                    }
                    jobSchedulingService.cancel(id);
                });
        // Re-fetch and render the current status
        return getQueueStatsContent(user, timezone, model);
    }

    /**
     * A user may only see their own jobs. Admins additionally see system jobs, which have no owner.
     */
    private boolean isVisibleTo(JobMetadataRepository.JobMetadata metadata, User user) {
        Long ownerId = metadata.getUserId();
        return ownerId == null ? user.getRole() == Role.ADMIN : ownerId.equals(user.getId());
    }

    private static Collection<UUID> parentIds(List<JobMetadataRepository.JobMetadata>... parentGroups) {
        return Stream.of(parentGroups).flatMap(List::stream)
                .map(JobMetadataRepository.JobMetadata::getId)
                .toList();
    }

    private boolean isTerminal(JobState state) {
        return state == JobState.COMPLETED || state == JobState.FAILED;
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

        // JobInfo derives percent/text from the children counts when present,
        // otherwise it falls back to the parent's own progress values.
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
                base.progressPercentValue(),
                base.progressMessage()
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
        String jobDescription = i18n.translate("jobs.description.user", ownerLabel(metadata), metadata.getJobType());
        boolean canCancel = state == JobState.AWAITING;

        Long durationSeconds = null;
        if (isTerminal(state) && metadata.getFinishedAt() != null) {
            Instant start = metadata.getProcessingAt() != null ? metadata.getProcessingAt() : metadata.getEnqueuedAt();
            if (start != null) {
                durationSeconds = Duration.between(start, metadata.getFinishedAt()).getSeconds();
            }
        }

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
                durationSeconds,
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

    private String ownerLabel(JobMetadataRepository.JobMetadata metadata) {
        return metadata.getUserId() == null
                ? i18n.translate("jobs.system")
                : userJdbcService.findById(metadata.getUserId()).map(User::getUsername).orElse(null);
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