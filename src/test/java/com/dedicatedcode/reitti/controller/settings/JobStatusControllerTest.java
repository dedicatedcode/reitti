package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.service.JobContext;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.jobs.JobState;
import com.dedicatedcode.reitti.service.jobs.JobType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@IntegrationTest
class JobStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestingService testingService;

    @Autowired
    private JobSchedulingService jobSchedulingService;

    @Autowired
    private JobMetadataRepository jobMetadataRepository;

    @Autowired
    private Scheduler scheduler;

    private User admin;
    private JobDetail noopDetail;

    @BeforeEach
    void setUp() throws Exception {
        admin = testingService.admin();
        noopDetail = JobBuilder.newJob(NoopTask.class)
                .withIdentity("controller-test-noop-task")
                .storeDurably()
                .build();
        scheduler.addJob(noopDetail, true);
    }

    @Test
    void shouldShowPendingParentJob() throws Exception {
        UUID parentId = jobSchedulingService.createParentJob(admin, JobType.GPX_IMPORT, "controller-test-pending-marker");
        try {
            mockMvc.perform(get("/settings/queue-stats-content").with(user(admin)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("controller-test-pending-marker")));
        } finally {
            jobSchedulingService.cancel(parentId);
        }
    }

    @Test
    void shouldShowFinishedJob() throws Exception {
        UUID jobId = jobSchedulingService.createParentJob(admin, JobType.GPX_IMPORT, "controller-test-finished-marker");
        try {
            jobMetadataRepository.updateState(jobId, JobState.COMPLETED, Instant.now());

            mockMvc.perform(get("/settings/queue-stats-content").with(user(admin)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("controller-test-finished-marker")));
        } finally {
            jobMetadataRepository.delete(jobId);
        }
    }

    @Test
    void shouldCancelParentAndCascadeChildrenViaEndpoint() throws Exception {
        UUID parentId = jobSchedulingService.createParentJob(admin, JobType.GPX_IMPORT, "controller-test-cancel-parent");

        UUID child1 = scheduleChild(parentId);
        UUID child2 = scheduleChild(parentId);

        assertTrue(jobMetadataRepository.findById(child1).isPresent());
        assertTrue(jobMetadataRepository.findById(child2).isPresent());

        mockMvc.perform(delete("/settings/job/{id}", parentId).with(csrf()).with(user(admin)))
                .andExpect(status().isOk());

        assertFalse(jobMetadataRepository.findById(parentId).isPresent());
        assertFalse(jobMetadataRepository.findById(child1).isPresent());
        assertFalse(jobMetadataRepository.findById(child2).isPresent());
    }

    @Test
    void shouldHandleCancelOfUnknownJob() throws Exception {
        mockMvc.perform(delete("/settings/job/{id}", UUID.randomUUID()).with(csrf()).with(user(admin)))
                .andExpect(status().isOk());
    }


    @Test
    void shouldShowTheFailureReasonForAFailedJob() throws Exception {
        UUID jobId = jobSchedulingService.createParentJob(admin, JobType.INTERVALS_ICU_IMPORT, "controller-test-failed-marker");
        try {
            jobMetadataRepository.updateProgress(jobId, 0, 0,
                    "intervals.icu rejected the request: 429 TOO_MANY_REQUESTS");
            jobMetadataRepository.updateState(jobId, JobState.FAILED, Instant.now());

            mockMvc.perform(get("/settings/queue-stats-content").with(user(admin)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("controller-test-failed-marker")))
                    .andExpect(content().string(containsString("intervals.icu rejected the request: 429 TOO_MANY_REQUESTS")));
        } finally {
            jobMetadataRepository.delete(jobId);
        }
    }

    @Test
    void shouldNotShowTheProgressTextOfACompletedJob() throws Exception {
        UUID jobId = jobSchedulingService.createParentJob(admin, JobType.GPX_IMPORT, "controller-test-completed-marker");
        try {
            jobMetadataRepository.updateProgress(jobId, 3, 3, "controller-test-stale-progress-text");
            jobMetadataRepository.updateState(jobId, JobState.COMPLETED, Instant.now());

            mockMvc.perform(get("/settings/queue-stats-content").with(user(admin)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("controller-test-completed-marker")))
                    .andExpect(content().string(not(containsString("controller-test-stale-progress-text"))));
        } finally {
            jobMetadataRepository.delete(jobId);
        }
    }

    private UUID scheduleChild(UUID parentId) {
        List<UUID> before = jobMetadataRepository.findByStates(List.of(JobState.AWAITING)).stream()
                .map(JobMetadataRepository.JobMetadata::getId)
                .toList();

        jobSchedulingService.scheduleTask(noopDetail, new NoopTaskData().withParentJobId(parentId),
                Instant.now().plusSeconds(60),
                JobSchedulingService.Metadata.builder()
                        .user(admin)
                        .jobType(JobType.GPS_IMPORT)
                        .friendlyName("controller-test-cancel-child")
                        .build());

        List<UUID> after = jobMetadataRepository.findByStates(List.of(JobState.AWAITING)).stream()
                .map(JobMetadataRepository.JobMetadata::getId)
                .toList();
        assertEquals(before.size() + 1, after.size());
        return after.stream()
                .filter(id -> !before.contains(id))
                .findFirst()
                .orElseThrow();
    }

    public static class NoopTaskData extends JobContext<NoopTaskData> {
        public NoopTaskData() {
        }

        private NoopTaskData(UUID jobId, UUID parentJobId) {
            super(jobId, parentJobId);
        }

        @Override
        public NoopTaskData withJobId(UUID jobId) {
            return new NoopTaskData(jobId, parentJobId);
        }

        @Override
        public NoopTaskData withParentJobId(UUID parentId) {
            return new NoopTaskData(jobId, parentId);
        }
    }

    public static class NoopTask implements Job {
        @Override
        public void execute(JobExecutionContext context) {
        }
    }
}
