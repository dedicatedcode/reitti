package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.UserType;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.importer.PromotionJobHandler;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.jobs.PromotionInflightGuard;
import com.dedicatedcode.reitti.service.processing.LocationPointStagingService;
import org.junit.jupiter.api.Test;
import org.quartz.JobDetail;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Verifies the #1212 trigger flood fix: while a promotion for a partition is scheduled or
 * running, further flushes of the same partition must not enqueue additional promotion
 * triggers - the queued trigger count has to stay bounded, otherwise Quartz starves.
 */
class LocationBatchingServiceTest {

    private final LocationPointStagingService stagingService = mock(LocationPointStagingService.class);
    private final JobDetail promotionTask = mock(JobDetail.class);
    private final JobSchedulingService jobScheduler = mock(JobSchedulingService.class);
    private final PromotionInflightGuard promotionInflightGuard = new PromotionInflightGuard();

    private final User user = new User(1L, "tester", null, "Tester", null, null, Role.USER, UserType.NORMAL, 0L);
    private final Device device = new Device(3L, "phone", true, true, true, "#ffffff", true, Instant.now(), Instant.now(), 0L);

    private String partitionKey() {
        return String.format("stream_%d_%s_%s", user.getId(), device.id(), LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)).toLowerCase();
    }

    @Test
    void enqueuesOnlyOnePromotionWhileAnotherIsInFlight() {
        LocationBatchingService service = new LocationBatchingService(stagingService, promotionTask, jobScheduler, promotionInflightGuard, 1, 5);

        // first point fills the batch (maxBatchSize=1) -> flush -> promotion enqueued
        service.addLocationPoint(user, device, point("2026-08-27T10:00:00Z"));
        verify(jobScheduler, times(1)).enqueueTask(eq(promotionTask), any(PromotionJobHandler.TaskData.class), any());

        // further flushes while that promotion is scheduled/running must not create new triggers
        service.addLocationPoint(user, device, point("2026-08-27T10:00:05Z"));
        service.addLocationPoint(user, device, point("2026-08-27T10:00:10Z"));
        verify(jobScheduler, times(1)).enqueueTask(eq(promotionTask), any(PromotionJobHandler.TaskData.class), any());

        // the promoted rows are staged nevertheless, so the pending promotion picks them up
        verify(stagingService, times(3)).insertBatch(eq(partitionKey()), eq(user), eq(device), any());
    }

    @Test
    void enqueuesAgainAfterTheRunningPromotionReleasedThePartition() {
        LocationBatchingService service = new LocationBatchingService(stagingService, promotionTask, jobScheduler, promotionInflightGuard, 1, 5);

        service.addLocationPoint(user, device, point("2026-08-27T10:00:00Z"));
        verify(jobScheduler, times(1)).enqueueTask(eq(promotionTask), any(PromotionJobHandler.TaskData.class), any());

        // PromotionJobHandler releases the partition after execution
        promotionInflightGuard.release(partitionKey());

        service.addLocationPoint(user, device, point("2026-08-27T10:00:05Z"));
        verify(jobScheduler, times(2)).enqueueTask(eq(promotionTask), any(PromotionJobHandler.TaskData.class), any());
    }

    @Test
    void releaseOnFailedEnqueueDoesNotBlockFurtherPromotions() {
        LocationBatchingService service = new LocationBatchingService(stagingService, promotionTask, jobScheduler, promotionInflightGuard, 1, 5);

        org.mockito.Mockito.doThrow(new IllegalStateException("scheduler down"))
                .when(jobScheduler).enqueueTask(eq(promotionTask), any(PromotionJobHandler.TaskData.class), any());
        service.addLocationPoint(user, device, point("2026-08-27T10:00:00Z"));

        // enqueue failed -> guard must have been released again, otherwise the partition
        // would never be promoted until restart
        service.addLocationPoint(user, device, point("2026-08-27T10:00:05Z"));
        verify(jobScheduler, times(2)).enqueueTask(eq(promotionTask), any(PromotionJobHandler.TaskData.class), any());
    }

    private LocationPoint point(String timestamp) {
        LocationPoint locationPoint = new LocationPoint();
        locationPoint.setTimestamp(Instant.parse(timestamp));
        locationPoint.setLatitude(53.551086);
        locationPoint.setLongitude(9.993682);
        locationPoint.setAccuracyMeters(10.0);
        return locationPoint;
    }
}
