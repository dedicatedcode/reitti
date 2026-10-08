package com.dedicatedcode.reitti.service.jobs;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobDetail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;

@IntegrationTest
class VisitSensitivityRecalculationTransactionTest {


    @Autowired
    @Qualifier("visitSensitivityRecalculationJob")
    private JobDetail recalculationJob;

    @Autowired
    private JobSchedulingService jobSchedulingService;

    @MockitoSpyBean
    private JobMetadataRepository jobMetadataRepository;

    @Autowired
    private TestingService testingService;

    private User user;

    @BeforeEach
    void setUp() {
        testingService.clearData();
        user = testingService.randomUser();
        testingService.newSignificantPlace(user);
    }

    @Test
    void shouldCompleteAndSpawnThePipelineOnTheQuartzThread() {
        UUID parentId = jobSchedulingService.createParentJob(user, JobType.MANUAL_MODIFICATION, "recalc-parent");

        UUID jobId = scheduleRecalculation(parentId);

        await().atMost(60, TimeUnit.SECONDS).until(() -> isTerminal(jobId));

        assertEquals(JobState.COMPLETED, jobMetadataRepository.getState(jobId).orElseThrow(),
                "recalculation must not fail; an IllegalStateException here means "
                        + "enqueueTaskAfterCommit ran without an active transaction");

        await().atMost(30, TimeUnit.SECONDS).until(() -> hasPipelineUnder(parentId));
        jobSchedulingService.cancel(parentId);
    }

    @Test
    void shouldNotSpawnThePipelineWhenTheTransactionRollsBack() throws InterruptedException {
        UUID parentId = jobSchedulingService.createParentJob(user, JobType.MANUAL_MODIFICATION, "recalc-rollback-parent");

        // Fails only after the deletes and the enqueue, so the enqueue really was registered
        failProgressWritesFor();

        UUID jobId = scheduleRecalculation(parentId);

        await().atMost(60, TimeUnit.SECONDS).until(() -> isTerminal(jobId));
        assertEquals(JobState.FAILED, jobMetadataRepository.getState(jobId).orElseThrow());

        // give a (wrongly) eagerly-scheduled pipeline time to show up
        Thread.sleep(2000);
        assertFalse(hasPipelineUnder(parentId),
                "the pipeline was scheduled even though the recalculation transaction rolled back; "
                        + "it would process rows the task had deleted but not committed");

        jobSchedulingService.cancel(parentId);
    }

    private void failProgressWritesFor() {
        List<String> failing = List.of(new String[]{"Done", "Failed"});
        doAnswer(invocation -> {
            if (failing.contains(invocation.getArgument(3, String.class))) {
                throw new IllegalStateException("injected failure for progress message: "
                        + invocation.getArgument(3, String.class));
            }
            return null;
        }).when(jobMetadataRepository).updateProgress(any(UUID.class), anyLong(), anyLong(), anyString());
    }

    private UUID scheduleRecalculation(UUID parentId) {
        jobSchedulingService.enqueueTask(recalculationJob,
                new VisitSensitivityConfigurationRecalculationTask.TaskData(user.getId()).withParentJobId(parentId),
                JobSchedulingService.Metadata.builder()
                        .user(user)
                        .friendlyName("recalc-transaction-test")
                        .jobType(JobType.DATA_RECALCULATION)
                        .build());

        return await().atMost(30, TimeUnit.SECONDS)
                .until(() -> jobMetadataRepository.findByParentJobId(parentId).stream()
                                .filter(j -> j.getJobType() == JobType.DATA_RECALCULATION)
                                .map(JobMetadataRepository.JobMetadata::getId)
                                .findFirst().orElse(null),
                       Objects::nonNull);
    }

    private boolean isTerminal(UUID jobId) {
        JobState state = jobMetadataRepository.getState(jobId).orElseThrow();
        return state == JobState.COMPLETED || state == JobState.FAILED;
    }

    private boolean hasPipelineUnder(UUID parentId) {
        return jobMetadataRepository.findByParentJobId(parentId).stream()
                .anyMatch(j -> j.getJobType() == JobType.LOCATION_PROCESSING);
    }
}