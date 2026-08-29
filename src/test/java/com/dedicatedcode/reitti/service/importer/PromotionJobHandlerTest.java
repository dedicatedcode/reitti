package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.UserType;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.service.UserNotificationService;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.jobs.PromotionInflightGuard;
import com.dedicatedcode.reitti.service.processing.LocationDataCleanupTask;
import com.dedicatedcode.reitti.service.processing.LocationPointStagingService;
import com.dedicatedcode.reitti.service.processing.LocationPointStagingService.PromotionResult;
import com.dedicatedcode.reitti.service.processing.TimeRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Documents the promotion/cleanup hand-off fixed for #1212.
 * <p>
 * promote() returns the exact time range of the rows it actually inserted, so the cleanup job
 * is always scheduled with the range of the data that was just promoted - independent of
 * duplicate queued promotion jobs or points arriving from the ingest thread while the
 * promotion runs. In v5.3.0 the range was looked up in the staging table before promoting,
 * which could return null (NPE at TimeRange.start()) or a too-wide range.
 */
@ExtendWith(MockitoExtension.class)
class PromotionJobHandlerTest {

    private static final Instant FLUSH_START = Instant.parse("2026-08-27T22:59:55Z");
    private static final Instant FLUSH_END = Instant.parse("2026-08-27T23:00:00Z");
    private static final String PARTITION_KEY = "stream_2_3_20260827";
    private static final UUID JOB_ID = UUID.randomUUID();

    @Mock
    private LocationPointStagingService stagingService;
    @Mock
    private JobSchedulingService jobSchedulingService;
    @Mock
    private JobMetadataRepository metadataRepository;
    @Mock
    private UserNotificationService userNotificationService;
    @Mock
    private JobDetail locationDataCleanupTask;
    @Mock
    private JobDetail liveModeOnlyUpdateTask;
    @Mock
    private JobDetail promotionTask;
    @Mock
    private JobExecutionContext context;

    private final PromotionInflightGuard promotionInflightGuard = new PromotionInflightGuard();

    private PromotionJobHandler handler;

    private final User user = new User(1L, "tester", null, "Tester", null, null, Role.USER, UserType.NORMAL, 0L);
    private final Device device = new Device(3L, "phone", true, true, true, "#ffffff", true, Instant.now(), Instant.now(), 0L);

    @BeforeEach
    void setUp() {
        this.handler = new PromotionJobHandler(stagingService,
                jobSchedulingService,
                metadataRepository,
                userNotificationService,
                promotionInflightGuard,
                locationDataCleanupTask,
                liveModeOnlyUpdateTask,
                promotionTask);
    }

    @Test
    void shouldScheduleCleanupWithActuallyPromotedRange() throws JobExecutionException {
        when(stagingService.promote(user, PARTITION_KEY)).thenReturn(new PromotionResult(5, new TimeRange(FLUSH_START, FLUSH_END)));
        when(stagingService.hasUnpromotedPoints(PARTITION_KEY)).thenReturn(false);

        runHandler();

        ArgumentCaptor<LocationDataCleanupTask.TaskData> captor = ArgumentCaptor.forClass(LocationDataCleanupTask.TaskData.class);
        verify(jobSchedulingService).enqueueTask(eq(locationDataCleanupTask), captor.capture(), any());
        assertEquals(FLUSH_START, captor.getValue().getStart());
        assertEquals(FLUSH_END, captor.getValue().getEnd());
        verify(userNotificationService).newLocationData(eq(user), eq(device), any(TimeRange.class));
        verify(jobSchedulingService, never()).enqueueTask(eq(promotionTask), any(), any());
    }

    @Test
    void shouldNotScheduleCleanupWhenNothingWasPromoted() throws JobExecutionException {
        when(stagingService.promote(user, PARTITION_KEY)).thenReturn(new PromotionResult(0, TimeRange.empty()));
        when(stagingService.hasUnpromotedPoints(PARTITION_KEY)).thenReturn(false);

        runHandler();

        verify(jobSchedulingService, never()).enqueueTask(any(), any(), any());
        verify(userNotificationService, never()).newLocationData(any(), any(), any());
    }

    @Test
    void shouldReDrivePromotionWhenPointsRemainUnpromoted() throws JobExecutionException {
        // points were flushed by the ingest thread while this promotion was running
        when(stagingService.promote(user, PARTITION_KEY)).thenReturn(new PromotionResult(0, TimeRange.empty()));
        when(stagingService.hasUnpromotedPoints(PARTITION_KEY)).thenReturn(true);

        runHandler();

        ArgumentCaptor<PromotionJobHandler.TaskData> captor = ArgumentCaptor.forClass(PromotionJobHandler.TaskData.class);
        verify(jobSchedulingService).enqueueTask(eq(promotionTask), captor.capture(), any());
        assertEquals(PARTITION_KEY, captor.getValue().getPartitionKey());
        verify(jobSchedulingService, never()).enqueueTask(eq(locationDataCleanupTask), any(), any());
    }

    @Test
    void shouldReleaseInflightGuardAfterExecution() throws JobExecutionException {
        // the batching service acquired the guard before enqueueing this promotion
        assertTrue(promotionInflightGuard.tryAcquire(PARTITION_KEY));
        when(stagingService.promote(user, PARTITION_KEY)).thenReturn(new PromotionResult(0, TimeRange.empty()));
        when(stagingService.hasUnpromotedPoints(PARTITION_KEY)).thenReturn(false);

        runHandler();

        // the guard was released, so the next flush may enqueue a promotion again
        assertTrue(promotionInflightGuard.tryAcquire(PARTITION_KEY));
    }

    private void runHandler() throws JobExecutionException {
        JobDataMap dataMap = new JobDataMap();
        dataMap.put("data", new PromotionJobHandler.TaskData(user, device, PARTITION_KEY, false, JOB_ID, null));
        when(context.getMergedJobDataMap()).thenReturn(dataMap);
        handler.execute(context);
    }
}
