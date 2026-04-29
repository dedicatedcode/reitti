package com.dedicatedcode.reitti.service.jobs;

import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.filters.JobClientFilter;
import org.jobrunr.jobs.filters.JobServerFilter;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class JobMetadataListener implements JobClientFilter, JobServerFilter {
    private final JobMetadataRepository jobMetadataRepository;

    public JobMetadataListener(JobMetadataRepository jobMetadataRepository) {
        this.jobMetadataRepository = jobMetadataRepository;
    }

    @Override
    public void onProcessing(Job job) {
        UUID id = job.getId();
        this.jobMetadataRepository.updateState(id, JobState.RUNNING, Instant.now());
    }

    @Override
    public void onProcessingSucceeded(Job job) {
        UUID id = job.getId();
        this.jobMetadataRepository.updateState(id, JobState.COMPLETED, Instant.now());
    }

    @Override
    public void onProcessingFailed(Job job, Exception e) {
        UUID id = job.getId();
        this.jobMetadataRepository.updateState(id, JobState.FAILED, Instant.now());
    }

    @Override
    public void onFailedAfterRetries(Job job) {
        UUID id = job.getId();
        this.jobMetadataRepository.updateState(id, JobState.FAILED, Instant.now());
    }
}
