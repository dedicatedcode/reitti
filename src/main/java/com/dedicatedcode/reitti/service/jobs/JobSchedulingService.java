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

    public void enqueue(JobLambda job, Metadata jobMetaData) {
        UUID jobId = UUID.randomUUID();
        enqueue(jobId, job, new Metadata(jobId, jobMetaData.user, jobMetaData.jobType, jobMetaData.friendlyName));
    }
    public void enqueue(UUID jobId, JobLambda job, Metadata jobMetaData) {
        Instant now = Instant.now();
        jobMetadataRepository.insert(jobId, jobMetaData.user, jobMetaData.jobType, jobMetaData.friendlyName, JobState.CREATED, now, null);
        jobScheduler.enqueue(job);
    }

    /**
     * Schedule a pre-built Job with associated metadata to run at a specific time.
     */
    public void schedule(UUID jobId, JobLambda job, LocalDateTime scheduledTime, Metadata jobMetaData) {
        Instant now = Instant.now();
        Instant scheduledAt = scheduledTime.atZone(ZoneId.systemDefault()).toInstant();
        jobMetadataRepository.insert(jobId, jobMetaData.user, jobMetaData.jobType, jobMetaData.friendlyName, JobState.AWAITING, now, scheduledAt);
        jobScheduler.schedule(jobId, scheduledTime, job);
    }

    public record Metadata(UUID jobId, User user, JobType jobType, String friendlyName) {
        public static class Builder {
            private UUID jobId;
            private User user;
            private JobType jobType;
            private String friendlyName;

            public Builder jobId(UUID jobId) {
                this.jobId = jobId;
                return this;
            }

            public Builder user(User user) {
                this.user = user;
                return this;
            }

            public Builder jobType(JobType jobType) {
                this.jobType = jobType;
                return this;
            }

            public Builder friendlyName(String friendlyName) {
                this.friendlyName = friendlyName;
                return this;
            }

            public Metadata build() {
                return new Metadata(jobId, user, jobType, friendlyName);
            }
        }

        public static Builder builder() {
            return new Builder();
        }
    }
}
