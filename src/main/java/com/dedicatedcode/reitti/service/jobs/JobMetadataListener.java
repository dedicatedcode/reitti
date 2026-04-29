package com.dedicatedcode.reitti.service.jobs;

import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.service.jobs.JobType;
import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.filters.JobServerFilter;
import org.jobrunr.jobs.states.EnqueuedState;
import org.jobrunr.jobs.states.ScheduledState;
import org.jobrunr.jobs.states.State;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class JobMetadataListener implements JobServerFilter {
    private final JobMetadataRepository jobMetadataRepository;

    public JobMetadataListener(JobMetadataRepository jobMetadataRepository) {
        this.jobMetadataRepository = jobMetadataRepository;
    }

    @Override
    public void onCreate(Job job) {
        UUID jobId = job.getId();
        Long userId = (Long) job.getMetadata().get("userId");
        String jobTypeStr = (String) job.getMetadata().get("jobType");
        String friendlyName = (String) job.getMetadata().get("friendlyName");
        JobType jobType = jobTypeStr != null ? JobType.valueOf(jobTypeStr) : null;

        if (userId == null || jobType == null || friendlyName == null) {
            return;
        }

        State initialState = job.getState();
        Instant enqueuedAt = null;
        Instant scheduledAt = null;

        if (initialState instanceof EnqueuedState) {
            enqueuedAt = Instant.now();
        } else if (initialState instanceof ScheduledState) {
            ScheduledState scheduledState = (ScheduledState) initialState;
            scheduledAt = scheduledState.getScheduledAt();
            enqueuedAt = Instant.now();
        }

        jobMetadataRepository.insert(jobId, userId, jobType, friendlyName, initialState.name(), enqueuedAt, scheduledAt);
    }

    @Override
    public void onUpdate(Job job, Job oldJob) {
        State newState = job.getState();
        State oldState = oldJob.getState();

        if (newState.name().equals(oldState.name())) {
            return;
        }

        UUID jobId = job.getId();
        Instant stateTimestamp = Instant.now();
        jobMetadataRepository.updateState(jobId, newState.name(), stateTimestamp);
    }
}
