package com.dedicatedcode.reitti.service.jobs;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.service.JobContext;
import org.junit.jupiter.api.Test;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class JobSchedulingServiceIntegrationTest {

    @Autowired
    private JobSchedulingService jobSchedulingService;

    @Autowired
    private JobMetadataRepository jobMetadataRepository;

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private TestingService testingService;

    private static TriggerKey taskTriggerKey(UUID jobId) {
        return TriggerKey.triggerKey(jobId.toString(), JobSchedulingService.TASK_TRIGGER_GROUP);
    }

    @Test
    void shouldScheduleTaskAsAwaitingAndCompleteIt() throws Exception {
        RecordingTask.executions.clear();
        JobDetail detail = JobBuilder.newJob(RecordingTask.class)
                .withIdentity("integration-test-recording-task")
                .storeDurably()
                .build();
        scheduler.addJob(detail, true);

        jobSchedulingService.enqueueTask(detail, new TaskData(), JobSchedulingService.Metadata.builder()
                .user(testingService.randomUser())
                .jobType(JobType.GPS_IMPORT)
                .friendlyName("integration-test-recording")
                .build());

        await().atMost(30, TimeUnit.SECONDS).until(() -> !RecordingTask.executions.isEmpty());
        UUID jobId = RecordingTask.executions.keySet().iterator().next();

        await().atMost(30, TimeUnit.SECONDS)
                .until(() -> jobMetadataRepository.getState(jobId).orElse(null) == JobState.COMPLETED);

        JobMetadataRepository.JobMetadata metadata = jobMetadataRepository.findById(jobId).orElseThrow();
        assertEquals(JobType.GPS_IMPORT, metadata.getJobType());
        assertFalse(scheduler.checkExists(taskTriggerKey(jobId)));

        jobMetadataRepository.delete(jobId);
    }

    @Test
    void shouldBeVisibleWhileScheduledInTheFuture() throws Exception {
        JobDetail detail = JobBuilder.newJob(RecordingTask.class)
                .withIdentity("integration-test-future-task")
                .storeDurably()
                .build();
        scheduler.addJob(detail, true);

        jobSchedulingService.scheduleTask(detail, new TaskData(), Instant.now().plusSeconds(30), JobSchedulingService.Metadata.builder()
                .user(testingService.randomUser())
                .jobType(JobType.GPS_IMPORT)
                .friendlyName("integration-test-future")
                .build());

        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> jobMetadataRepository.findByStates(List.of(JobState.AWAITING)).stream()
                        .anyMatch(j -> "integration-test-future".equals(j.getFriendlyName())));

        for (JobMetadataRepository.JobMetadata job : jobMetadataRepository.findByStates(List.of(JobState.AWAITING))) {
            if ("integration-test-future".equals(job.getFriendlyName())) {
                jobSchedulingService.cancel(job.getId());
                assertTrue(jobMetadataRepository.findById(job.getId()).isEmpty());
                assertFalse(scheduler.checkExists(taskTriggerKey(job.getId())));
            }
        }
    }

    @Test
    void shouldDeferWithoutCreatingNewMetadata() throws Exception {
        DeferringOnceTask.executions.clear();
        User user = testingService.randomUser();
        JobDetail detail = JobBuilder.newJob(DeferringOnceTask.class)
                .withIdentity("integration-test-defer-task")
                .storeDurably()
                .build();
        scheduler.addJob(detail, true);

        jobSchedulingService.enqueueTask(detail, new TaskData(), JobSchedulingService.Metadata.builder()
                .user(user)
                .jobType(JobType.H3_CELL_UPDATE)
                .friendlyName("integration-test-defer")
                .build());

        await().atMost(30, TimeUnit.SECONDS).until(() -> !DeferringOnceTask.executions.isEmpty());
        UUID jobId = DeferringOnceTask.executions.keySet().iterator().next();

        await().atMost(30, TimeUnit.SECONDS)
                .until(() -> jobMetadataRepository.getState(jobId).orElse(null) == JobState.COMPLETED);

        assertEquals(2, DeferringOnceTask.executions.get(jobId));
        assertTrue(jobMetadataRepository.findByParentJobId(jobId).isEmpty());

        List<JobMetadataRepository.JobMetadata> rowsForJob = jobMetadataRepository.findByStates(List.of(JobState.COMPLETED)).stream()
                .filter(j -> jobId.equals(j.getId()))
                .toList();
        assertEquals(1, rowsForJob.size());
        assertEquals("test defer", rowsForJob.getFirst().getProgressMessage());

        assertFalse(scheduler.checkExists(taskTriggerKey(jobId)));
        Set<org.quartz.TriggerKey> leftover = scheduler.getTriggerKeys(
                org.quartz.impl.matchers.GroupMatcher.triggerGroupEquals(JobSchedulingService.TASK_TRIGGER_GROUP));
        assertTrue(leftover.stream().noneMatch(k -> k.getName().startsWith(jobId.toString())));

        jobMetadataRepository.delete(jobId);
    }

    @Test
    void shouldCancelParentAndRemoveChildTriggers() throws Exception {
        User user = testingService.randomUser();
        UUID parentId = jobSchedulingService.createParentJob(user, JobType.GPX_IMPORT, "integration-test-parent");

        JobDetail detail = JobBuilder.newJob(RecordingTask.class)
                .withIdentity("integration-test-cancel-task")
                .storeDurably()
                .build();
        scheduler.addJob(detail, true);

        jobSchedulingService.scheduleTask(detail, new TaskData().withParentJobId(parentId), Instant.now().plusSeconds(60), JobSchedulingService.Metadata.builder()
                .user(user)
                .jobType(JobType.GPS_IMPORT)
                .friendlyName("integration-test-child-1")
                .build());
        jobSchedulingService.scheduleTask(detail, new TaskData().withParentJobId(parentId), Instant.now().plusSeconds(60), JobSchedulingService.Metadata.builder()
                .user(user)
                .jobType(JobType.GPS_IMPORT)
                .friendlyName("integration-test-child-2")
                .build());

        List<UUID> childIds = jobMetadataRepository.findByParentJobId(parentId).stream()
                .map(JobMetadataRepository.JobMetadata::getId)
                .toList();
        assertEquals(2, childIds.size());

        for (UUID childId : childIds) {
            assertTrue(scheduler.checkExists(taskTriggerKey(childId)));
        }

        jobSchedulingService.cancel(parentId);

        assertTrue(jobMetadataRepository.findById(parentId).isEmpty());
        assertTrue(jobMetadataRepository.findByParentJobId(parentId).isEmpty());
        for (UUID childId : childIds) {
            assertFalse(scheduler.checkExists(taskTriggerKey(childId)));
        }
    }

    public static class TaskData extends JobContext<TaskData> {
        public TaskData() {
        }

        private TaskData(UUID jobId, UUID parentJobId) {
            super(jobId, parentJobId);
        }

        @Override
        public TaskData withJobId(UUID jobId) {
            return new TaskData(jobId, parentJobId);
        }

        @Override
        public TaskData withParentJobId(UUID parentId) {
            return new TaskData(jobId, parentId);
        }
    }

    public static class RecordingTask implements Job {
        static final Map<UUID, Integer> executions = new ConcurrentHashMap<>();

        @Override
        public void execute(JobExecutionContext context) {
            UUID jobId = UUID.fromString(context.getMergedJobDataMap().getString("jobId"));
            executions.merge(jobId, 1, Integer::sum);
        }
    }

    public static class DeferringOnceTask implements Job {
        static final Map<UUID, Integer> executions = new ConcurrentHashMap<>();

        @Autowired
        private JobSchedulingService jobSchedulingService;

        @Override
        public void execute(JobExecutionContext context) {
            UUID jobId = UUID.fromString(context.getMergedJobDataMap().getString("jobId"));
            int run = executions.merge(jobId, 1, Integer::sum);
            if (run == 1) {
                boolean deferred = jobSchedulingService.defer(context, Duration.ofMillis(250), "test defer");
                if (!deferred) {
                    throw new IllegalStateException("expected job to be deferrable");
                }
            }
        }
    }
}
