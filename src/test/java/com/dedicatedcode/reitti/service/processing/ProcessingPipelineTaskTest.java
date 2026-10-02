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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
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
    private PlatformTransactionManager transactionManager;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-01T00:00:00Z"));
    private final UserProcessingLock userProcessingLock = new UserProcessingLock();
    private final BatchFailureTracker batchFailureTracker = new BatchFailureTracker(clock);
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
                transactionManager);
    }

    @Test
    void processesAndMarksBatchInOneTransaction() {
        when(userJdbcService.findByUsername("tester")).thenReturn(Optional.of(user));
        when(rawLocationPointJdbcService.countUnprocessedByUser(user)).thenReturn(2L);
        List<RawLocationPoint> batch = List.of(pt(0), pt(60));
        when(rawLocationPointJdbcService.findByUserAndProcessedIsFalseOrderByTimestampWithLimit(user, 10, 0))
                .thenReturn(batch)
                .thenReturn(List.of());

        run();

        InOrder inOrder = inOrder(transactionManager, locationProcessTask, rawLocationPointJdbcService);
        inOrder.verify(transactionManager).getTransaction(any());
        inOrder.verify(locationProcessTask).processLocationEvent(any(LocationProcessEvent.class));
        inOrder.verify(rawLocationPointJdbcService).bulkUpdateProcessedStatus(batch);
        inOrder.verify(transactionManager).commit(any());
        verify(jobMetadataRepository).updateProgress(JOB_ID, 2, 2L, "Done");
    }

    @Test
    void rollsBackAndStopsRunWithoutMarkingWhenProcessingFails() {
        when(userJdbcService.findByUsername("tester")).thenReturn(Optional.of(user));
        when(rawLocationPointJdbcService.countUnprocessedByUser(user)).thenReturn(2L);
        List<RawLocationPoint> batch = List.of(pt(0), pt(60));
        when(rawLocationPointJdbcService.findByUserAndProcessedIsFalseOrderByTimestampWithLimit(user, 10, 0))
                .thenReturn(batch);
        doThrow(new RuntimeException("boom")).when(locationProcessTask).processLocationEvent(any());

        run();

        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
        verify(rawLocationPointJdbcService, never()).bulkUpdateProcessedStatus(anyList());
        verify(rawLocationPointJdbcService, times(1)).findByUserAndProcessedIsFalseOrderByTimestampWithLimit(user, 10, 0);
        verify(jobMetadataRepository).updateProgress(JOB_ID, 0, 2L, "Failed");
    }

    @Test
    void keepsRepeatedlyFailingBatchUnprocessedAndContinuesAfterIt() {
        when(userJdbcService.findByUsername("tester")).thenReturn(Optional.of(user));
        when(rawLocationPointJdbcService.countUnprocessedByUser(user)).thenReturn(4L);
        batchFailureTracker.recordFailure(user, T0, T0.plusSeconds(60));
        batchFailureTracker.recordFailure(user, T0, T0.plusSeconds(60));

        List<RawLocationPoint> failingBatch = List.of(pt(0), pt(60));
        List<RawLocationPoint> nextBatch = List.of(pt(120), pt(180));
        when(rawLocationPointJdbcService.findByUserAndProcessedIsFalseOrderByTimestampWithLimit(user, 10, 0))
                .thenReturn(failingBatch);
        when(rawLocationPointJdbcService.findByUserAndProcessedIsFalseAndTimestampAfterOrderByTimestampWithLimit(user, T0.plusSeconds(60), 10))
                .thenReturn(nextBatch)
                .thenReturn(List.of());
        doThrow(new RuntimeException("poison"))
                .doNothing()
                .when(locationProcessTask).processLocationEvent(any());

        run();

        verify(rawLocationPointJdbcService, never()).bulkUpdateProcessedStatus(failingBatch);
        verify(rawLocationPointJdbcService).bulkUpdateProcessedStatus(nextBatch);
        verify(jobMetadataRepository).updateProgress(JOB_ID, 2, 4L, "Done, 2 point(s) deferred after repeated failures");
    }

    @Test
    void retriesBackedOffBatchOnlyAfterTheBackoff() {
        when(userJdbcService.findByUsername("tester")).thenReturn(Optional.of(user));
        when(rawLocationPointJdbcService.countUnprocessedByUser(user)).thenReturn(2L);
        for (int i = 0; i < BatchFailureTracker.MAX_CONSECUTIVE_FAILURES; i++) {
            batchFailureTracker.recordFailure(user, T0, T0.plusSeconds(60));
        }
        List<RawLocationPoint> failedBatch = List.of(pt(0), pt(60));
        when(rawLocationPointJdbcService.findByUserAndProcessedIsFalseOrderByTimestampWithLimit(user, 10, 0))
                .thenReturn(failedBatch);
        when(rawLocationPointJdbcService.findByUserAndProcessedIsFalseAndTimestampAfterOrderByTimestampWithLimit(user, T0.plusSeconds(60), 10))
                .thenReturn(List.of());

        run();

        verify(locationProcessTask, never()).processLocationEvent(any());
        verify(rawLocationPointJdbcService, never()).bulkUpdateProcessedStatus(anyList());

        clock.advance(BatchFailureTracker.INITIAL_BACKOFF);
        when(rawLocationPointJdbcService.findByUserAndProcessedIsFalseOrderByTimestampWithLimit(user, 10, 0))
                .thenReturn(failedBatch)
                .thenReturn(List.of());

        run();

        verify(locationProcessTask).processLocationEvent(argThat(event -> event.getEarliest().equals(T0)));
        verify(rawLocationPointJdbcService).bulkUpdateProcessedStatus(failedBatch);
        verify(jobMetadataRepository).updateProgress(JOB_ID, 2, 2L, "Done");
    }

    private void run() {
        task.execute(new ProcessingPipelineTask.TaskData("tester", null, null, JOB_ID, null));
    }

    private RawLocationPoint pt(long offsetSeconds) {
        return new RawLocationPoint((long) offsetSeconds + 1, null, T0.plusSeconds(offsetSeconds),
                new GeoPoint(52.5, 13.4), 10.0, null, false, false, 0L);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
