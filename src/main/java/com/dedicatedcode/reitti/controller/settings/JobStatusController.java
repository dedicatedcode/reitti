package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.controller.api.QueueStatsApiController.JobInfo;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ImportJobRepository;
import com.dedicatedcode.reitti.service.jobs.JobState;
import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.navigation.OffsetBasedPageRequest;
import org.jobrunr.storage.navigation.PageRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/settings")
public class JobStatusController {

    private final StorageProvider storageProvider;
    private final boolean dataManagementEnabled;
    private final ImportJobRepository importJobRepository;
    private final OffsetBasedPageRequest amountRequest = new OffsetBasedPageRequest("updatedAt:ASC", 0, 100);

    public JobStatusController(StorageProvider storageProvider,
                               @Value("${reitti.data-management.enabled:false}") boolean dataManagementEnabled,
                               ImportJobRepository importJobRepository) {
        this.storageProvider = storageProvider;
        this.dataManagementEnabled = dataManagementEnabled;
        this.importJobRepository = importJobRepository;
    }

    @GetMapping("/job-status")
    public String getJobStatus(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("activeSection", "job-status");
        model.addAttribute("isAdmin", user.getRole() == Role.ADMIN);
        model.addAttribute("dataManagementEnabled", dataManagementEnabled);

        List<JobInfo> pendingJobs = new ArrayList<>();
        List<JobInfo> pastJobs = new ArrayList<>();

        // Get pending/running jobs (ENQUEUED, PROCESSING, SCHEDULED)
        List<Job> enqueuedJobs = storageProvider.getJobList(StateName.ENQUEUED, amountRequest);
        for (Job job : enqueuedJobs) {
            JobInfo jobInfo = mapToJobInfo(job);
            if (jobInfo != null) {
                pendingJobs.add(jobInfo);
            }
        }

        List<Job> processingJobs = storageProvider.getJobList(StateName.PROCESSING, amountRequest);
        for (Job job : processingJobs) {
            JobInfo jobInfo = mapToJobInfo(job);
            if (jobInfo != null) {
                pendingJobs.add(jobInfo);
            }
        }

        List<Job> scheduledJobs = storageProvider.getJobList(StateName.SCHEDULED, amountRequest);
        for (Job job : scheduledJobs) {
            JobInfo jobInfo = mapToJobInfo(job);
            if (jobInfo != null) {
                pendingJobs.add(jobInfo);
            }
        }

        // Get past jobs (SUCCEEDED, FAILED)
        List<Job> failedJobs = storageProvider.getJobList(StateName.FAILED, amountRequest);
        for (Job job : failedJobs) {
            JobInfo jobInfo = mapToJobInfo(job);
            if (jobInfo != null) {
                pastJobs.add(jobInfo);
            }
        }

        List<Job> succeededJobs = storageProvider.getJobList(StateName.SUCCEEDED, amountRequest);
        for (Job job : succeededJobs) {
            JobInfo jobInfo = mapToJobInfo(job);
            if (jobInfo != null) {
                pastJobs.add(jobInfo);
            }
        }

        // Sort pending jobs by enqueued time (most recent first)
        pendingJobs.sort((a, b) -> compareInstant(b.enqueuedAt(), a.enqueuedAt()));

        // Sort past jobs by finished time (most recent first)
        pastJobs.sort((a, b) -> compareInstant(b.finishedAt(), a.finishedAt()));


        model.addAttribute("pendingJobs", pendingJobs);
        model.addAttribute("pastJobs", pastJobs);

        return "settings/job-status";
    }

    @GetMapping("/queue-stats-content")
    public String getQueueStatsContent(Model model) {
        List<JobInfo> pendingJobs = new ArrayList<>();
        List<JobInfo> pastJobs = new ArrayList<>();

        // Get pending/running jobs (ENQUEUED, PROCESSING, SCHEDULED)
        List<Job> enqueuedJobs = storageProvider.getJobList(StateName.ENQUEUED, amountRequest);
        for (Job job : enqueuedJobs) {
            JobInfo jobInfo = mapToJobInfo(job);
            if (jobInfo != null) {
                pendingJobs.add(jobInfo);
            }
        }

        List<Job> processingJobs = storageProvider.getJobList(StateName.PROCESSING, amountRequest);
        for (Job job : processingJobs) {
            JobInfo jobInfo = mapToJobInfo(job);
            if (jobInfo != null) {
                pendingJobs.add(jobInfo);
            }
        }

        List<Job> scheduledJobs = storageProvider.getJobList(StateName.SCHEDULED, amountRequest);
        for (Job job : scheduledJobs) {
            JobInfo jobInfo = mapToJobInfo(job);
            if (jobInfo != null) {
                pendingJobs.add(jobInfo);
            }
        }

        // Get past jobs (SUCCEEDED, FAILED)
        List<Job> failedJobs = storageProvider.getJobList(StateName.FAILED, amountRequest);
        for (Job job : failedJobs) {
            JobInfo jobInfo = mapToJobInfo(job);
            if (jobInfo != null) {
                pastJobs.add(jobInfo);
            }
        }

        List<Job> succeededJobs = storageProvider.getJobList(StateName.SUCCEEDED, amountRequest);
        for (Job job : succeededJobs) {
            JobInfo jobInfo = mapToJobInfo(job);
            if (jobInfo != null) {
                pastJobs.add(jobInfo);
            }
        }

        model.addAttribute("pendingJobs", pendingJobs);
        model.addAttribute("pastJobs", pastJobs);

        return "settings/job-status :: queue-stats-content";
    }

    private JobInfo mapToJobInfo(Job job) {
        try {
            UUID jobId = job.getId();
            String state = job.getState().name();
            Instant enqueuedAt = job.getCreatedAt();
            Instant scheduledAt = job.getCreatedAt();
            Instant processingAt = job.getUpdatedAt();
            Instant finishedAt = job.getUpdatedAt();

            // Get job details for more info
            String jobName = job.getJobName();
            String jobDescription = job.getJobDetails() != null ? job.getJobDetails().toString() : "";

            // Check if this is an ImportJob that can be cancelled
            boolean canCancel = false;
            if (state.equals("ENQUEUED") || state.equals("PROCESSING") || state.equals("SCHEDULED")) {
                Optional<JobState> importJobState = importJobRepository.getState(jobId);
                if (importJobState.isPresent() && importJobState.get() == JobState.AWAITING) {
                    canCancel = true;
                }
            }

            return new JobInfo(
                    jobId,
                    jobName,
                    jobDescription,
                    state,
                    enqueuedAt,
                    scheduledAt,
                    processingAt,
                    finishedAt,
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