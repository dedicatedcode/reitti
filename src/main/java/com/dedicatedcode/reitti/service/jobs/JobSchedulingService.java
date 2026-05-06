package com.dedicatedcode.reitti.service.jobs;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.service.JobContext;
import com.github.kagkarlsson.scheduler.CurrentlyExecuting;
import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.event.SchedulerListener;
import com.github.kagkarlsson.scheduler.task.Execution;
import com.github.kagkarlsson.scheduler.task.ExecutionComplete;
import com.github.kagkarlsson.scheduler.task.Task;
import com.github.kagkarlsson.scheduler.task.TaskInstanceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class JobSchedulingService implements SchedulerListener {
    private static final Logger log = LoggerFactory.getLogger(JobSchedulingService.class);

    private final ObjectProvider<Scheduler> jobScheduler;
    private final JobMetadataRepository jobMetadataRepository;

    public JobSchedulingService(ObjectProvider<Scheduler> jobScheduler, JobMetadataRepository jobMetadataRepository) {
        this.jobScheduler = jobScheduler;
        this.jobMetadataRepository = jobMetadataRepository;
    }

    public <T extends JobContext<T>> void scheduleTask(Task<T> task, T data, Instant scheduledAt, Metadata meta) {
        scheduleTask(UUID.randomUUID(), task, data, scheduledAt, meta);
    }

    private <T extends JobContext<T>> void scheduleTask(UUID jobId, Task<T> task, T data, Instant scheduledAt, Metadata meta) {
        Instant now = Instant.now();

        JobState state = scheduledAt.isAfter(now) ? JobState.AWAITING : JobState.CREATED;
        jobMetadataRepository.insert(jobId, meta.user, meta.jobType, meta.friendlyName, state, now, scheduledAt, data.getParentJobId());

        T updatedData = data.withJobId(jobId);
        if (data.getParentJobId() != null) {
            updatedData = updatedData.withParentJobId(data.getParentJobId());
        }
        jobScheduler.getObject().schedule(task.instance(jobId.toString(), updatedData), scheduledAt);
    }

    public <T extends JobContext<T>> void enqueueTask(Task<T> task, T data, Metadata meta) {
        scheduleTask(task, data, Instant.now(), meta);
    }

    public void cancel(String taskId, UUID jobId) {
        jobScheduler.getObject().cancel(TaskInstanceId.of(taskId, jobId.toString()));
    }

    public UUID createParentJob(User user, JobType jobType, String friendlyName) {
        UUID parentJobId = UUID.randomUUID();
        Instant now = Instant.now();

        jobMetadataRepository.insert(
                parentJobId,
                user,
                jobType,
                friendlyName,
                JobState.AWAITING,
                now,
                now,
                null
        );

        return parentJobId;
    }

    @Override
    public void onExecutionScheduled(TaskInstanceId taskInstanceId, Instant executionTime) {
        UUID jobId = UUID.fromString(taskInstanceId.getId());
        this.jobMetadataRepository.updateState(jobId, JobState.AWAITING, Instant.now());
        log.trace("Job with ID {} is in the state of {}", jobId, JobState.AWAITING);
    }

    @Override
    public void onExecutionStart(CurrentlyExecuting currentlyExecuting) {
        UUID jobId = UUID.fromString(currentlyExecuting.getTaskInstance().getId());
        this.jobMetadataRepository.updateState(jobId, JobState.RUNNING, Instant.now());
        log.trace("Job with ID {} is now in the state of {}", jobId, JobState.RUNNING);
        Optional<JobMetadataRepository.JobMetadata> metadata = jobMetadataRepository.findById(jobId);
        if (metadata.isPresent() && metadata.get().getParentJobId() != null) {
            jobMetadataRepository.updateParentJobState(metadata.get().getParentJobId(), JobState.RUNNING);
        }
    }

    @Override
    public void onExecutionComplete(ExecutionComplete executionComplete) {
        UUID jobId = UUID.fromString(executionComplete.getExecution().getId());
        JobState state = executionComplete.getResult() == ExecutionComplete.Result.OK ?  JobState.COMPLETED : JobState.FAILED;
        this.jobMetadataRepository.updateState(jobId, state, Instant.now());
        if (state == JobState.FAILED) {
            log.error("Job with ID {} failed", jobId, executionComplete.getCause().orElseThrow());
        } else {
            log.trace("Job with ID {} is now in the state of {}", jobId, state);
        }
        Optional<JobMetadataRepository.JobMetadata> metadata = jobMetadataRepository.findById(jobId);
        if (metadata.isPresent() && metadata.get().getParentJobId() != null) {
            jobMetadataRepository.updateParentJobState(metadata.get().getParentJobId(), state);
        }
    }

    @Override
    public void onExecutionDead(Execution execution) {

    }

    @Override
    public void onExecutionFailedHeartbeat(CurrentlyExecuting currentlyExecuting) {

    }

    @Override
    public void onSchedulerEvent(SchedulerEventType type) {

    }

    @Override
    public void onCandidateEvent(CandidateEventType type) {

    }

    public void cancel(UUID jobId) {
        this.jobMetadataRepository.delete(jobId);
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
