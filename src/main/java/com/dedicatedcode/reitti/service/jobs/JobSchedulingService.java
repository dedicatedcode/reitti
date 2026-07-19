package com.dedicatedcode.reitti.service.jobs;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.service.JobContext;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JobSchedulingService implements JobListener {
    private static final Logger log = LoggerFactory.getLogger(JobSchedulingService.class);

    private final Scheduler scheduler;
    private final JobMetadataRepository jobMetadataRepository;

    public JobSchedulingService(Scheduler scheduler, JobMetadataRepository jobMetadataRepository) throws SchedulerException {
        this.scheduler = scheduler;
        this.jobMetadataRepository = jobMetadataRepository;
        this.scheduler.getListenerManager().addJobListener(this);
    }

    @Override
    public String getName() {
        return "JobMetadataListener";
    }

    @Override
    public void jobToBeExecuted(JobExecutionContext context) {
        UUID jobId = UUID.fromString(context.getTrigger().getKey().getName());
        this.jobMetadataRepository.updateState(jobId, JobState.RUNNING, Instant.now());
        log.trace("Job with ID {} is now in the state of {}", jobId, JobState.RUNNING);

        Optional<JobMetadataRepository.JobMetadata> metadata = jobMetadataRepository.findById(jobId);
        if (metadata.isPresent() && metadata.get().getParentJobId() != null) {
            jobMetadataRepository.updateParentJobState(metadata.get().getParentJobId(), JobState.RUNNING);
        }
    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {}

    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        UUID jobId = UUID.fromString(context.getTrigger().getKey().getName());
        JobState state = (jobException == null) ? JobState.COMPLETED : JobState.FAILED;
        this.jobMetadataRepository.updateState(jobId, state, Instant.now());

        if (state == JobState.FAILED) {
            log.error("Job with ID {} failed", jobId, jobException);
        } else {
            log.trace("Job with ID {} is now in the state of {}", jobId, state);
        }

        Optional<JobMetadataRepository.JobMetadata> metadata = jobMetadataRepository.findById(jobId);
        if (metadata.isPresent() && metadata.get().getParentJobId() != null) {
            jobMetadataRepository.updateParentJobState(metadata.get().getParentJobId(), state);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T extends JobContext<T>> void scheduleSystemTask(JobDetail jobDetail, T data, Instant scheduledAt, Metadata meta) {
        scheduleTask(jobDetail, data, scheduledAt, meta);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T extends JobContext<T>> void scheduleTask(JobDetail jobDetail, T data, Instant scheduledAt, Metadata meta) {
        UUID jobId = UUID.randomUUID();
        Instant now = Instant.now();

        JobState state = scheduledAt.isAfter(now) ? JobState.AWAITING : JobState.CREATED;
        if (meta.user() == null) {
            jobMetadataRepository.insert(jobId, jobDetail.getKey().getName(), meta.jobType(), meta.friendlyName(), state, now, scheduledAt, data.getParentJobId());
        } else {
            jobMetadataRepository.insert(jobId, meta.user(), jobDetail.getKey().getName(), meta.jobType(), meta.friendlyName(), state, now, scheduledAt, data.getParentJobId());
        }

        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("jobId", jobId.toString());
        jobDataMap.put("data", data.withJobId(jobId));

        // Use jobId as the trigger name so we can easily retrieve it in the listener
        Trigger trigger = TriggerBuilder.newTrigger()
                .forJob(jobDetail)
                .withIdentity(jobId.toString())
                .usingJobData(jobDataMap)
                .startAt(Date.from(scheduledAt))
                .build();

        try {
            scheduler.scheduleJob(trigger);
        } catch (SchedulerException e) {
            log.error("Failed to schedule job", e);
            throw new RuntimeException("Failed to schedule job", e);
        }
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T extends JobContext<T>> void enqueueTask(JobDetail jobDetail, T data, Metadata meta) {
        scheduleTask(jobDetail, data, Instant.now(), meta);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T extends JobContext<T>> void enqueueSystemTask(JobDetail jobDetail, T data, Metadata meta) {
        scheduleSystemTask(jobDetail, data, Instant.now(), meta);
    }

    public void cancel(UUID jobId) {
        log.info("Cancelling job {}", jobId);
        Optional<JobMetadataRepository.JobMetadata> meta = jobMetadataRepository.findById(jobId);
        if (meta.isPresent() && meta.get().getTaskId() != null) {
            try {
                scheduler.unscheduleJob(TriggerKey.triggerKey(jobId.toString()));
            } catch (SchedulerException e) {
                log.error("Failed to cancel job", e);
            }
        }
        jobMetadataRepository.delete(jobId);
    }

    public UUID createParentJob(User user, JobType jobType, String friendlyName) {
        UUID parentJobId = UUID.randomUUID();
        Instant now = Instant.now();

        jobMetadataRepository.insert(
                parentJobId,
                user,
                null,
                jobType,
                friendlyName,
                JobState.AWAITING,
                now,
                now,
                null
        );

        return parentJobId;
    }

    public record Metadata(User user, JobType jobType, String friendlyName) {
        public static class Builder {
            private User user;
            private JobType jobType;
            private String friendlyName;
            private boolean resume = true;

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

            public Builder resume(boolean resume) {
                this.resume = resume;
                return this;
            }
        }

        public static Builder builder() {
            return new Builder();
        }
    }
}