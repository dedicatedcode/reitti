package com.dedicatedcode.reitti.service.jobs;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.service.JobContext;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JobSchedulingService implements JobListener {
    private static final Logger log = LoggerFactory.getLogger(JobSchedulingService.class);
    public static final String TASK_TRIGGER_GROUP = "reitti-tasks";

    private final Set<UUID> deferredJobs = ConcurrentHashMap.newKeySet();
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
        Optional<UUID> jobId = resolveJobId(context);
        jobId.ifPresent(id -> {
            this.jobMetadataRepository.updateState(id, JobState.RUNNING, Instant.now());
            log.trace("Job with ID {} is now in the state of {}", id, JobState.RUNNING);

            Optional<JobMetadataRepository.JobMetadata> metadata = jobMetadataRepository.findById(id);
            if (metadata.isPresent() && metadata.get().getParentJobId() != null) {
                jobMetadataRepository.updateParentJobState(metadata.get().getParentJobId(), JobState.RUNNING);
            }
        });
    }

    private Optional<UUID> resolveJobId(JobExecutionContext context) {
        Object fromDataMap = context.getMergedJobDataMap().get("jobId");
        if (fromDataMap instanceof String s) {
            try {
                return Optional.of(UUID.fromString(s));
            } catch (IllegalArgumentException e) {
                log.warn("Found non-UUID jobId in JobDataMap: {}", s);
                return Optional.empty();
            }
        }
        return parseJobIdFromTriggerName(context);
    }

    private Optional<UUID> parseJobIdFromTriggerName(JobExecutionContext context) {
        UUID jobId = null;
        try {
            jobId = UUID.fromString(context.getTrigger().getKey().getName());
        } catch (Exception e) {
            log.trace("Failed to parse job ID from trigger key: {}", context.getTrigger().getKey().getName());
        }
        return Optional.ofNullable(jobId);
    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {}

    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        Optional<UUID> jobId = resolveJobId(context);
        jobId.ifPresent(id -> {
            if (deferredJobs.remove(id)) {
                log.trace("Job with ID {} was deferred, skipping terminal state update", id);
                return;
            }
            JobState state = (jobException == null) ? JobState.COMPLETED : JobState.FAILED;
            this.jobMetadataRepository.updateState(id, state, Instant.now());

            if (state == JobState.FAILED) {
                log.error("Job with ID {} failed", id, jobException);
            } else {
                log.trace("Job with ID {} is now in the state of {}", id, state);
            }

            Optional<JobMetadataRepository.JobMetadata> metadata = jobMetadataRepository.findById(id);
            if (metadata.isPresent() && metadata.get().getParentJobId() != null) {
                jobMetadataRepository.updateParentJobState(metadata.get().getParentJobId(), state);
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T extends JobContext<T>> void scheduleTask(JobDetail jobDetail, T data, Instant scheduledAt, Metadata meta) {
        UUID jobId = UUID.randomUUID();
        Instant now = Instant.now();

        if (meta.user() == null) {
            jobMetadataRepository.insert(jobId, jobDetail.getKey().getName(), meta.jobType(), meta.friendlyName(), JobState.AWAITING, now, scheduledAt, data.getParentJobId());
        } else {
            jobMetadataRepository.insert(jobId, meta.user(), jobDetail.getKey().getName(), meta.jobType(), meta.friendlyName(), JobState.AWAITING, now, scheduledAt, data.getParentJobId());
        }

        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("jobId", jobId.toString());
        jobDataMap.put("data", data.withJobId(jobId));

        // Use jobId as the trigger name so we can easily retrieve it in the listener
        Trigger trigger = TriggerBuilder.newTrigger()
                .forJob(jobDetail)
                .withIdentity(jobId.toString(), TASK_TRIGGER_GROUP)
                .usingJobData(jobDataMap)
                .startAt(Date.from(scheduledAt))
                .build();

        try {
            scheduler.scheduleJob(trigger);
        } catch (SchedulerException e) {
            throw new RuntimeException(e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T extends JobContext<T>> void enqueueTask(JobDetail jobDetail, T data, Metadata meta) {
        scheduleTask(jobDetail, data, Instant.now(), meta);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public <T extends JobContext<T>> void enqueueTaskAfterCommit(JobDetail jobDetail, T data, Metadata meta) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                enqueueTask(jobDetail, data, meta);
            }
        });
    }

    public boolean defer(JobExecutionContext context, Duration delay, String reason) {
        Optional<UUID> jobIdOpt = resolveJobId(context);
        if (jobIdOpt.isEmpty()) {
            log.warn("Cannot defer untracked job {}", context.getJobDetail().getKey());
            return false;
        }
        UUID jobId = jobIdOpt.get();
        Instant nextRun = Instant.now().plus(delay);

        JobDataMap jobDataMap = new JobDataMap();
        context.getMergedJobDataMap().forEach((key, value) -> {
            if (!"jobId".equals(key)) {
                jobDataMap.put(key, value);
            }
        });
        jobDataMap.put("jobId", jobId.toString());

        Trigger trigger = TriggerBuilder.newTrigger()
                .forJob(context.getJobDetail())
                // unique name: the trigger that is currently executing still occupies the plain
                // jobId key and is only removed by quartz after the execution completes
                .withIdentity(jobId + ":" + UUID.randomUUID(), TASK_TRIGGER_GROUP)
                .usingJobData(jobDataMap)
                .startAt(Date.from(nextRun))
                .build();

        try {
            scheduler.scheduleJob(trigger);
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to reschedule deferred job " + jobId, e);
        }

        deferredJobs.add(jobId);
        jobMetadataRepository.updateState(jobId, JobState.AWAITING, Instant.now());
        jobMetadataRepository.updateProgress(jobId, 0, 0, reason);
        log.info("Deferred job {} to {} ({})", jobId, nextRun, reason);
        return true;
    }

    public void cancel(UUID jobId) {
        log.info("Cancelling job {}", jobId);
        unscheduleTriggers(jobId);

        List<UUID> childIds = jobMetadataRepository.findByParentJobId(jobId).stream()
                .map(JobMetadataRepository.JobMetadata::getId)
                .toList();
        for (UUID childId : childIds) {
            unscheduleTriggers(childId);
        }
        // deleting the parent cascades to child metadata rows
        jobMetadataRepository.delete(jobId);
    }

    private void unscheduleTriggers(UUID jobId) {
        Set<TriggerKey> keys = new HashSet<>();
        keys.add(TriggerKey.triggerKey(jobId.toString()));
        try {
            scheduler.getTriggerKeys(GroupMatcher.triggerGroupEquals(TASK_TRIGGER_GROUP)).stream()
                    .filter(key -> key.getName().equals(jobId.toString()) || key.getName().startsWith(jobId + ":"))
                    .forEach(keys::add);
        } catch (SchedulerException e) {
            log.debug("Could not scan triggers for job {}", jobId, e);
        }
        for (TriggerKey key : keys) {
            try {
                scheduler.unscheduleJob(key);
            } catch (SchedulerException e) {
                log.debug("No trigger to remove for job {} ({})", jobId, key);
            }
        }
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