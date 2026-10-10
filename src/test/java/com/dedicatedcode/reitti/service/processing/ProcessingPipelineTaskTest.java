package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.event.LocationProcessEvent;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.UserType;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.repository.PreviewRawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.UserNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessingPipelineTaskTest {

    private static final Instant T0 = Instant.parse("2026-08-01T10:00:00Z");
    private static final UUID JOB_ID = UUID.randomUUID();

    @Mock
    private RawLocationPointJdbcService rawLocationPointJdbcService;
    @Mock
    private PreviewRawLocationPointJdbcService previewRawLocationPointJdbcService;
    @Mock
    private UserJdbcService userJdbcService;
    @Mock
    private JobMetadataRepository jobMetadataRepository;
    @Mock
    private UnifiedLocationProcessingService locationProcessTask;
    @Mock
    private UserNotificationService userNotificationService;

    private final UserProcessingLock userProcessingLock = new UserProcessingLock();
    private final BatchFailureTracker batchFailureTracker = new BatchFailureTracker();
    private final User user = new User(1L, "tester", null, "Tester", null, null, Role.USER, UserType.NORMAL, 0L);

    private ProcessingPipelineTask task;

    @BeforeEach
    void setUp() {
        task = new ProcessingPipelineTask(rawLocationPointJdbcService,
                previewRawLocationPointJdbcService,
                userJdbcService,
                jobMetadataRepository,
                10,
                locationProcessTask,
                userProcessingLock,
                batchFailureTracker,
                userNotificationService);
    }

    @Test
    void marksBatchAsProcessedOnlyAfterSuccessfulProcessing() {
        when(userJdbcService.findByUsername("tester")).thenReturn(Optional.of(user));
        when(rawLocationPointJdbcService.countUnprocessedByUser(user)).thenReturn(2L);
        List<RawLocationPoint> batch = List.of(pt(0), pt(60));
        when(rawLocationPointJdbcService.findByUserAndProcessedIsFalseOrderByTimestampWithLimit(user, 10, 0))
                .thenReturn(batch)
                .thenReturn(List.of());

        task.execute(new ProcessingPipelineTask.TaskData("tester", null, null, JOB_ID, null));

        InOrder inOrder = inOrder(locationProcessTask, rawLocationPointJdbcService);
        inOrder.verify(locationProcessTask).processLocationEvent(any(LocationProcessEvent.class));
        inOrder.verify(rawLocationPointJdbcService).bulkUpdateProcessedStatus(batch);
        verify(jobMetadataRepository).updateProgress(JOB_ID, 2, 2L, "Done");
    }

    @Test
    void stopsRunWithoutMarkingWhenProcessingFails() {
        when(userJdbcService.findByUsername("tester")).thenReturn(Optional.of(user));
        when(rawLocationPointJdbcService.countUnprocessedByUser(user)).thenReturn(2L);
        List<RawLocationPoint> batch = List.of(pt(0), pt(60));
        when(rawLocationPointJdbcService.findByUserAndProcessedIsFalseOrderByTimestampWithLimit(user, 10, 0))
                .thenReturn(batch);
        doThrow(new RuntimeException("boom")).when(locationProcessTask).processLocationEvent(any());

        task.execute(new ProcessingPipelineTask.TaskData("tester", null, null, JOB_ID, null));

        verify(rawLocationPointJdbcService, never()).bulkUpdateProcessedStatus(anyList());
        verify(rawLocationPointJdbcService, times(1)).findByUserAndProcessedIsFalseOrderByTimestampWithLimit(user, 10, 0);
        verify(jobMetadataRepository).updateProgress(JOB_ID, 0, 2L, "Failed");
    }

    @Test
    void forceMarksBatchAfterRepeatedFailuresAndContinues() {
        when(userJdbcService.findByUsername("tester")).thenReturn(Optional.of(user));
        when(rawLocationPointJdbcService.countUnprocessedByUser(user)).thenReturn(4L);
        failureTrackerRecordTwice(T0);

        List<RawLocationPoint> failingBatch = List.of(pt(0), pt(60));
        List<RawLocationPoint> nextBatch = List.of(pt(120), pt(180));
        when(rawLocationPointJdbcService.findByUserAndProcessedIsFalseOrderByTimestampWithLimit(user, 10, 0))
                .thenReturn(failingBatch)
                .thenReturn(nextBatch)
                .thenReturn(List.of());
        doThrow(new RuntimeException("poison"))
                .doNothing()
                .when(locationProcessTask).processLocationEvent(any());

        task.execute(new ProcessingPipelineTask.TaskData("tester", null, null, JOB_ID, null));

        verify(rawLocationPointJdbcService).bulkUpdateProcessedStatus(failingBatch);
        verify(rawLocationPointJdbcService).bulkUpdateProcessedStatus(nextBatch);
        verify(jobMetadataRepository).updateProgress(JOB_ID, 4, 4L, "Done");
    }

    @Test
    void fullReprocessWalksEveryPointByKeysetWithoutTouchingTheProcessedFlag() {
        when(userJdbcService.findByUsername("tester")).thenReturn(Optional.of(user));
        when(rawLocationPointJdbcService.countByUser(user)).thenReturn(4L);

        List<RawLocationPoint> batch1 = List.of(pt(0), pt(60));
        List<RawLocationPoint> batch2 = List.of(pt(120), pt(180));
        when(rawLocationPointJdbcService.findByUserOrderByTimestampWithKeyset(user, null, 10))
                .thenReturn(batch1);
        when(rawLocationPointJdbcService.findByUserOrderByTimestampWithKeyset(user, batch1.getLast().getTimestamp(), 10))
                .thenReturn(batch2);
        when(rawLocationPointJdbcService.findByUserOrderByTimestampWithKeyset(user, batch2.getLast().getTimestamp(), 10))
                .thenReturn(List.of());

        task.execute(new ProcessingPipelineTask.TaskData("tester", null, null, JOB_ID, null).withFullReprocess());

        verify(locationProcessTask, times(2)).processLocationEvent(any(LocationProcessEvent.class));
        // The whole point: no per-row writes at all, and never the incremental read
        verify(rawLocationPointJdbcService, never()).bulkUpdateProcessedStatus(anyList());
        verify(rawLocationPointJdbcService, never()).findByUserAndProcessedIsFalseOrderByTimestampWithLimit(any(), anyInt(), anyInt());
        verify(rawLocationPointJdbcService, never()).countUnprocessedByUser(any());
        verify(jobMetadataRepository).updateProgress(JOB_ID, 4, 4L, "Done");
    }

    @Test
    void fullReprocessSurvivesTaskDataSerializationRoundTrip() {
        ProcessingPipelineTask.TaskData data =
                new ProcessingPipelineTask.TaskData("tester", null, "trace", JOB_ID, UUID.randomUUID()).withFullReprocess();

        ProcessingPipelineTask.TaskData parsed = ProcessingPipelineTask.TaskData.fromJson(data.toJson());

        assertTrue(parsed.isFullReprocess());
        assertEquals("tester", parsed.getUsername());
        assertEquals("trace", parsed.getTraceId());
        assertEquals(JOB_ID, parsed.getJobId());
    }

    @Test
    void fullReprocessFlagSurvivesParentJobIdAssignment() {
        ProcessingPipelineTask.TaskData data =
                new ProcessingPipelineTask.TaskData("tester", null, null).withFullReprocess();

        assertTrue(data.withParentJobId(UUID.randomUUID()).isFullReprocess(),
                "withParentJobId must not silently drop the full reprocess flag");
    }

    @Test
    void taskDataWithoutFullReprocessPropertyDefaultsToIncrementalMode() {
        ProcessingPipelineTask.TaskData parsed = ProcessingPipelineTask.TaskData.fromJson(
                "{\"username\":\"tester\",\"previewId\":null,\"traceId\":null,\"jobId\":\"" + JOB_ID + "\"}");

        assertFalse(parsed.isFullReprocess(), "payloads serialized before this change must still deserialize");
    }

    @Test
    void fullReprocessSkipsFailedBatchOnlyAfterGivingUpAndStillAdvancesTheCursor() {
        when(userJdbcService.findByUsername("tester")).thenReturn(Optional.of(user));
        when(rawLocationPointJdbcService.countByUser(user)).thenReturn(2L);
        failureTrackerRecordTwice(T0);

        List<RawLocationPoint> failingBatch = List.of(pt(0), pt(60));
        when(rawLocationPointJdbcService.findByUserOrderByTimestampWithKeyset(user, null, 10))
                .thenReturn(failingBatch);
        when(rawLocationPointJdbcService.findByUserOrderByTimestampWithKeyset(user, failingBatch.getLast().getTimestamp(), 10))
                .thenReturn(List.of());
        doThrow(new RuntimeException("poison")).when(locationProcessTask).processLocationEvent(any());

        task.execute(new ProcessingPipelineTask.TaskData("tester", null, null, JOB_ID, null).withFullReprocess());

        // Cursor advanced past the poisoned batch instead of writing the processed flag
        verify(rawLocationPointJdbcService).findByUserOrderByTimestampWithKeyset(user, failingBatch.getLast().getTimestamp(), 10);
        verify(rawLocationPointJdbcService, never()).bulkUpdateProcessedStatus(anyList());
        verify(jobMetadataRepository).updateProgress(JOB_ID, 2, 2L, "Done");
    }

    private void failureTrackerRecordTwice(Instant batchStart) {
        batchFailureTracker.recordFailure(user, batchStart);
        batchFailureTracker.recordFailure(user, batchStart);
    }

    private RawLocationPoint pt(long offsetSeconds) {
        return new RawLocationPoint((long) offsetSeconds + 1, null, T0.plusSeconds(offsetSeconds),
                new GeoPoint(52.5, 13.4), 10.0, null, false, false, 0L);
    }
}
