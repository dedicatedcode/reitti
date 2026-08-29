package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * Demonstrates the throughput problem behind #1212 ("promotion advances at ~1/8 real-time").
 * <p>
 * Every promotion - no matter how small the flush - schedules a LocationDataCleanupTask.
 * That task widens the range by the anomaly lookback (reitti.geo-point-filter.history-lookback-hours,
 * default 24h) on both sides and hands that range to UpdateCuratedTimelineTask, which deletes
 * every raw_location_points row in the window (dropForReSeeding) and re-inserts it as
 * processed = false (updateFromDevices). The pipeline then re-detects visits/trips for all of them.
 * <p>
 * So promoting a few seconds of new data re-opens ~48h of already processed history. With the
 * default batching config a promotion job is enqueued every ~5s per stream, the chain
 * (promotion -> cleanup -> reseed -> pipeline) can never keep up with real time, jobs pile up
 * faster than they drain and the whole Quartz machinery saturates.
 */
@IntegrationTest
class PromotionCleanupChurnTest {

    private static final Instant HISTORY_START = Instant.parse("2026-08-25T00:00:00Z");
    private static final Instant HISTORY_END = Instant.parse("2026-08-26T23:55:00Z");
    private static final UUID JOB_ID = UUID.randomUUID();

    @Autowired
    private LocationDataCleanupTask locationDataCleanupTask;
    @Autowired
    private SourceLocationPointJdbcService sourceLocationPointJdbcService;
    @Autowired
    private RawLocationPointJdbcService rawLocationPointJdbcService;
    @Autowired
    private TestingService testingService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private JobSchedulingService jobSchedulingService;

    @Test
    void singlePromotedPointReopensTwoDaysOfAlreadyProcessedTimeline() {
        User user = testingService.randomUser();
        Device device = testingService.findDefaultDevice(user);

        // The state as promotion sees it: two days of points already promoted into raw_source_points
        List<LocationPoint> history = points(HISTORY_START, HISTORY_END, Duration.ofMinutes(5));
        assertEquals(576, sourceLocationPointJdbcService.bulkInsert(user, device, history));

        // --- promotion cycle 1: the flush [00:00, 00:05] was promoted ---
        Instant flushStart = HISTORY_START;
        Instant flushEnd = HISTORY_START.plus(Duration.ofMinutes(5));
        TimeRange reseedRange1 = runCleanupAndCaptureReseedRange(user, device, flushStart, flushEnd);

        // LocationDataCleanupTask hands the ±24h lookback window to the timeline update,
        // not the 5 minutes that were actually promoted
        assertEquals(Duration.ofHours(24), Duration.between(reseedRange1.start(), flushStart));
        assertTrue(Duration.between(reseedRange1.start(), reseedRange1.end()).compareTo(Duration.ofHours(48)) >= 0);

        reseedFromView(user, reseedRange1);

        // 289 timeline points were seeded although the flush contained only 2 points
        assertEquals(289, timelineCount(user));
        assertEquals(289, unprocessedCount(user));

        // the pipeline finished - all points are processed now
        markAllProcessed(user);
        assertEquals(289, processedCount(user));

        // --- promotion cycle 2: a single new point arrives ---
        Instant newPoint = HISTORY_END.plus(Duration.ofMinutes(1));
        assertEquals(1, sourceLocationPointJdbcService.bulkInsert(user, device, List.of(point(newPoint))));

        reset(jobSchedulingService);
        TimeRange reseedRange2 = runCleanupAndCaptureReseedRange(user, device, newPoint, newPoint);
        reseedFromView(user, reseedRange2);

        // the single new point wiped and re-opened the whole lookback window:
        // 288 already processed points are unprocessed again and will be re-detected
        assertEquals(288, processedCount(user));
        assertEquals(289, unprocessedCount(user), "one new point was promoted, but the whole window was re-opened");
        assertEquals(577, timelineCount(user));
    }

    private TimeRange runCleanupAndCaptureReseedRange(User user, Device device, Instant start, Instant end) {
        locationDataCleanupTask.execute(new LocationDataCleanupTask.TaskData(user, device, start, end, JOB_ID, null));

        ArgumentCaptor<UpdateCuratedTimelineTask.TaskData> captor = ArgumentCaptor.forClass(UpdateCuratedTimelineTask.TaskData.class);
        verify(jobSchedulingService).enqueueTask(any(), captor.capture(), any());
        return (TimeRange) ReflectionTestUtils.getField(captor.getValue(), "timeRange");
    }

    /**
     * Steps 1-2 of UpdateCuratedTimelineTask.execute(). fillGaps() (step 3) and the
     * processing pipeline (step 4) are skipped to keep this test deterministic - they only
     * add more work on top.
     */
    private void reseedFromView(User user, TimeRange reseedRange) {
        rawLocationPointJdbcService.dropForReSeeding(user, reseedRange);
        rawLocationPointJdbcService.updateFromDevices(user, reseedRange);
    }

    private void markAllProcessed(User user) {
        jdbcTemplate.update("UPDATE raw_location_points SET processed = true WHERE user_id = ?", user.getId());
    }

    private long timelineCount(User user) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM raw_location_points WHERE user_id = ?", Long.class, user.getId());
    }

    private long processedCount(User user) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM raw_location_points WHERE user_id = ? AND processed", Long.class, user.getId());
    }

    private long unprocessedCount(User user) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM raw_location_points WHERE user_id = ? AND NOT processed", Long.class, user.getId());
    }

    private List<LocationPoint> points(Instant start, Instant end, Duration step) {
        List<LocationPoint> result = new ArrayList<>();
        for (Instant t = start; !t.isAfter(end); t = t.plus(step)) {
            result.add(point(t));
        }
        return result;
    }

    private LocationPoint point(Instant timestamp) {
        LocationPoint locationPoint = new LocationPoint();
        locationPoint.setTimestamp(timestamp);
        locationPoint.setLatitude(53.551086);
        locationPoint.setLongitude(9.993682);
        locationPoint.setAccuracyMeters(10.0);
        return locationPoint;
    }
}
