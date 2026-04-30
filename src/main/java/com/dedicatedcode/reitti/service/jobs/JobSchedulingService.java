package com.dedicatedcode.reitti.service.jobs;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.Task;
import com.github.kagkarlsson.scheduler.task.TaskInstanceId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class JobSchedulingService {

    private final ObjectProvider<Scheduler> jobScheduler;
    private final JobMetadataRepository jobMetadataRepository;

    public JobSchedulingService(ObjectProvider<Scheduler> jobScheduler, JobMetadataRepository jobMetadataRepository) {
        this.jobScheduler = jobScheduler;
        this.jobMetadataRepository = jobMetadataRepository;
    }

    public <T> void scheduleTask(Task<T> task, T data, Instant scheduledAt, Metadata meta) {
        Instant now = Instant.now();

        UUID jobId = UUID.randomUUID();
        // 1. Maintain your custom metadata for your dashboard
        JobState state = scheduledAt.isAfter(now) ? JobState.AWAITING : JobState.CREATED;
        jobMetadataRepository.insert(jobId, meta.user, meta.jobType, meta.friendlyName, state, now, scheduledAt);

        // 2. Schedule the actual task in db-scheduler
        // We use the JobId as the "Instance ID" so we can cancel it later if needed
        jobScheduler.getObject().schedule(task.instance(jobId.toString(), data), scheduledAt);
    }

    public <T> void enqueueTask(Task<T> task, T data, Metadata meta) {
        scheduleTask(task, data, Instant.now(), meta);
    }

    public void cancel(String taskId, UUID jobId) {
        jobScheduler.getObject().cancel(TaskInstanceId.of(taskId, jobId.toString()));
    }

    public record Metadata(User user, JobType jobType, String friendlyName) {
        public static class Builder {
            private User user;
            private JobType jobType;
            private String friendlyName;

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
                return new Metadata(user, jobType, friendlyName);
            }
        }

        public static Builder builder() {
            return new Builder();
        }
    }
}
