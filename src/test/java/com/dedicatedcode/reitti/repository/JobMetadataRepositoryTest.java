package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.jobs.JobState;
import com.dedicatedcode.reitti.service.jobs.JobType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class JobMetadataRepositoryTest {
    @Autowired
    private JobMetadataRepository jobMetadataRepository;

    @Autowired
    private TestingService testingService;

    private User user;

    @BeforeEach
    void setUp() {
        this.user = this.testingService.randomUser();
    }

    @Test
    void shouldReturnEmptyOptional() {
        Optional<JobMetadataRepository.JobMetadata> byId = this.jobMetadataRepository.findById(UUID.randomUUID());
        assertFalse(byId.isPresent());
    }

    @Test
    void shouldInsertAndFindJobById() {
        UUID jobId = UUID.randomUUID();
        jobMetadataRepository.insert(jobId, user, "test-task", JobType.GPS_IMPORT, "friendly-name", JobState.AWAITING, Instant.now(), Instant.now(), null);

        Optional<JobMetadataRepository.JobMetadata> loaded = jobMetadataRepository.findById(jobId);
        assertTrue(loaded.isPresent());
        assertEquals("test-task", loaded.get().getTaskId());
        assertEquals(JobType.GPS_IMPORT, loaded.get().getJobType());
        assertEquals("friendly-name", loaded.get().getFriendlyName());
        assertEquals(JobState.AWAITING, loaded.get().getState());
        assertEquals(user.getId(), loaded.get().getUserId());
        assertNull(loaded.get().getParentJobId());

        jobMetadataRepository.delete(jobId);
        assertFalse(jobMetadataRepository.findById(jobId).isPresent());
    }

    @Test
    void shouldFindByStatesIncludingLegacyCreated() {
        UUID awaitingId = UUID.randomUUID();
        UUID legacyCreatedId = UUID.randomUUID();
        jobMetadataRepository.insert(awaitingId, user, "t1", JobType.GPS_IMPORT, "awaiting-job", JobState.AWAITING, Instant.now(), Instant.now(), null);
        jobMetadataRepository.insert(legacyCreatedId, user, "t2", JobType.GPS_IMPORT, "created-job", JobState.CREATED, Instant.now(), Instant.now(), null);

        List<UUID> foundIds = jobMetadataRepository.findByStates(List.of(JobState.PREPARING, JobState.CREATED, JobState.AWAITING, JobState.RUNNING))
                .stream()
                .map(JobMetadataRepository.JobMetadata::getId)
                .toList();

        assertTrue(foundIds.contains(awaitingId));
        assertTrue(foundIds.contains(legacyCreatedId));

        jobMetadataRepository.delete(awaitingId);
        jobMetadataRepository.delete(legacyCreatedId);
    }

    @Test
    void shouldDeleteChildrenWhenParentIsDeleted() {
        UUID parentId = UUID.randomUUID();
        UUID childA = UUID.randomUUID();
        UUID childB = UUID.randomUUID();

        jobMetadataRepository.insert(parentId, user, null, JobType.GPX_IMPORT, "parent", JobState.AWAITING, Instant.now(), Instant.now(), null);
        jobMetadataRepository.insert(childA, user, "child-a", JobType.GPX_IMPORT, "child-a", JobState.AWAITING, Instant.now(), Instant.now(), parentId);
        jobMetadataRepository.insert(childB, user, "child-b", JobType.GPX_IMPORT, "child-b", JobState.AWAITING, Instant.now(), Instant.now(), parentId);

        assertEquals(2, jobMetadataRepository.findByParentJobId(parentId).size());

        jobMetadataRepository.delete(parentId);

        assertFalse(jobMetadataRepository.findById(parentId).isPresent());
        assertFalse(jobMetadataRepository.findById(childA).isPresent());
        assertFalse(jobMetadataRepository.findById(childB).isPresent());
    }

    @Test
    void shouldPropagateRunningStateToParent() {
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();

        jobMetadataRepository.insert(parentId, user, null, JobType.GPX_IMPORT, "parent", JobState.AWAITING, Instant.now(), Instant.now(), null);
        jobMetadataRepository.insert(childId, user, "child", JobType.GPX_IMPORT, "child", JobState.AWAITING, Instant.now(), Instant.now(), parentId);

        jobMetadataRepository.updateState(childId, JobState.RUNNING, Instant.now());
        jobMetadataRepository.updateParentJobState(parentId, JobState.RUNNING);
        assertEquals(JobState.RUNNING, jobMetadataRepository.findById(parentId).orElseThrow().getState());

        jobMetadataRepository.delete(parentId);
    }

    @Test
    void shouldCompleteParentOnlyWhenAllChildrenAreTerminal() {
        UUID parentId = UUID.randomUUID();
        UUID childA = UUID.randomUUID();
        UUID childB = UUID.randomUUID();

        jobMetadataRepository.insert(parentId, user, null, JobType.GPX_IMPORT, "parent", JobState.RUNNING, Instant.now(), Instant.now(), null);
        jobMetadataRepository.insert(childA, user, "child-a", JobType.GPX_IMPORT, "child-a", JobState.RUNNING, Instant.now(), Instant.now(), parentId);
        jobMetadataRepository.insert(childB, user, "child-b", JobType.GPX_IMPORT, "child-b", JobState.RUNNING, Instant.now(), Instant.now(), parentId);

        // first child finishes -> parent must stay RUNNING
        jobMetadataRepository.updateState(childA, JobState.COMPLETED, Instant.now());
        jobMetadataRepository.updateParentJobState(parentId, JobState.COMPLETED);
        assertEquals(JobState.RUNNING, jobMetadataRepository.findById(parentId).orElseThrow().getState());

        // second child fails -> parent must become FAILED
        jobMetadataRepository.updateState(childB, JobState.FAILED, Instant.now());
        jobMetadataRepository.updateParentJobState(parentId, JobState.COMPLETED);
        assertEquals(JobState.FAILED, jobMetadataRepository.findById(parentId).orElseThrow().getState());

        jobMetadataRepository.delete(parentId);
    }

    @Test
    void shouldStoreAndReadProgress() {
        UUID jobId = UUID.randomUUID();
        jobMetadataRepository.insert(jobId, user, "task", JobType.REVERSE_GEOCODE, "geo", JobState.RUNNING, Instant.now(), Instant.now(), null);

        jobMetadataRepository.updateProgress(jobId, 7, 10, "working");

        JobMetadataRepository.JobMetadata loaded = jobMetadataRepository.findById(jobId).orElseThrow();
        assertEquals(7L, loaded.getCurrentProgress());
        assertEquals(10L, loaded.getMaxProgress());
        assertEquals("working", loaded.getProgressMessage());

        jobMetadataRepository.delete(jobId);
    }
}
