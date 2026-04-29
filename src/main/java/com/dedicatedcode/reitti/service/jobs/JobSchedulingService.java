package com.dedicatedcode.reitti.service.jobs;

import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.JobBuilder;
import org.jobrunr.jobs.JobRunnable;
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

    /**
     * Enqueue a pre-built Job with associated metadata.
     */
    public void enqueue(Job job, Long userId, JobType jobType, String friendlyName) {
        UUID jobId = job.getId();
        Instant now = Instant.now();
        jobMetadataRepository.insert(jobId, userId, jobType, friendlyName, "ENQUEUED", now, null);
        jobScheduler.enqueue(job);
    }

    /**
     * Enqueue a lambda-based job with associated metadata.
     */
    public void enqueue(JobRunnable jobRunnable, Long userId, JobType jobType, String friendlyName) {
        Job job = JobBuilder.aJob()
                .withId(UUID.randomUUID())
                .withDetails(jobRunnable)
                .build();
        enqueue(job, userId, jobType, friendlyName);
    }

    /**
     * Schedule a pre-built Job with associated metadata to run at a specific time.
     */
    public void schedule(Job job, LocalDateTime scheduledTime, Long userId, JobType jobType, String friendlyName) {
        UUID jobId = job.getId();
        Instant now = Instant.now();
        Instant scheduledAt = scheduledTime.atZone(ZoneId.systemDefault()).toInstant();
        jobMetadataRepository.insert(jobId, userId, jobType, friendlyName, "SCHEDULED", now, scheduledAt);
        jobScheduler.schedule(job, scheduledTime);
    }

    /**
     * Schedule a lambda-based job with associated metadata to run at a specific time.
     */
    public void schedule(JobRunnable jobRunnable, LocalDateTime scheduledTime, Long userId, JobType jobType, String friendlyName) {
        Job job = JobBuilder.aJob()
                .withId(UUID.randomUUID())
                .withDetails(jobRunnable)
                .build();
        schedule(job, scheduledTime, userId, jobType, friendlyName);
    }
}
