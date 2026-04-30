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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
        List<JobInfo> pendingJobs = new ArrayList<>();
        List<JobInfo> pastJobs = new ArrayList<>();

        // Get pending/running jobs
        List<JobMetadataRepository.JobMetadata> pendingJobMetadata = jobMetadataRepository.findByStates(
            List.of(JobState.PREPARING, JobState.AWAITING, JobState.RUNNING)
        );
        for (JobMetadataRepository.JobMetadata metadata : pendingJobMetadata) {
            JobInfo jobInfo = mapToJobInfo(metadata);
            if (jobInfo != null) {
                pendingJobs.add(jobInfo);
            }
        }

        // Get past jobs
        List<JobMetadataRepository.JobMetadata> pastJobMetadata = jobMetadataRepository.findByStates(
            List.of(JobState.COMPLETED, JobState.FAILED)
        );
        for (JobMetadataRepository.JobMetadata metadata : pastJobMetadata) {
            JobInfo jobInfo = mapToJobInfo(metadata);
            if (jobInfo != null) {
                pastJobs.add(jobInfo);
            }
        }

        model.addAttribute("pendingJobs", pendingJobs);
        model.addAttribute("pastJobs", pastJobs);

        return "settings/job-status :: queue-stats-content";
    }

    private JobInfo mapToJobInfo(JobMetadataRepository.JobMetadata metadata) {
        try {
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
                    canCancel
            );
        } catch (Exception e) {
            return null;
        }
    }

    private int compareInstant(Instant a, Instant b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return b.compareTo(a); // Most recent first
    }
}
