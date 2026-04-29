package com.dedicatedcode.reitti.service.jobs;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class JobSchedulingService {

    private final JobScheduler jobScheduler;
    private final JobMetadataRepository jobMetadataRepository;

    public JobSchedulingService(JobScheduler jobScheduler, JobMetadataRepository jobMetadataRepository) {
        this.jobScheduler = jobScheduler;
        this.jobMetadataRepository = jobMetadataRepository;
    }

    public void enqueue(UUID jobId, JobLambda job, User user, JobType jobType, String friendlyName) {
        Instant now = Instant.now();
        jobMetadataRepository.insert(jobId, user.getId(), jobType, friendlyName, JobState.RUNNING, now, null);
        jobScheduler.enqueue(job);
    }

    /**
     * Schedule a pre-built Job with associated metadata to run at a specific time.
     */
    public void schedule(UUID jobId, JobLambda job, LocalDateTime scheduledTime, User user, JobType jobType, String friendlyName) {
        Instant now = Instant.now();
        Instant scheduledAt = scheduledTime.atZone(ZoneId.systemDefault()).toInstant();
        jobMetadataRepository.insert(jobId, user.getId(), jobType, friendlyName, JobState.AWAITING, now, scheduledAt);
        jobScheduler.schedule(jobId, scheduledTime, job);
    }

    public record Metadata(UUID jobId, User user, JobType jobType, String friendlyName) {
        //add a builder here AI!
    }
}
