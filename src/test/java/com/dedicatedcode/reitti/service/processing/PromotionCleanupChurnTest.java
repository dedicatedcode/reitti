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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * Verifies the throughput fix for #1212 ("promotion advances at ~1/8 real-time").
 * <p>
 * Each promotion cycle used to widen the promoted range by a static ±24h lookback and reseed
 * (delete + re-insert as unprocessed) that whole window, so promoting a few seconds of data
 * re-opened ~48h of already processed history. The window is now resolved from the actual
 * boundary conditions (context margin, capped at maxInterpolationGapMinutes), so a tiny
 * promotion only re-opens the points within that margin.
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
    void tinyPromotionOnlyReopensTheContextMarginInsteadOfTwoDays() {
        User user = testingService.randomUser();
        Device device = testingService.findDefaultDevice(user);

        // The state as promotion sees it: two days of points already promoted into raw_source_points
        List<LocationPoint> history = points(HISTORY_START, HISTORY_END, Duration.ofMinutes(5));
        assertEquals(576, sourceLocationPointJdbcService.bulkInsert(user, device, history));

        // --- promotion cycle 1: the flush [00:00, 00:05] was promoted ---
        Instant flushStart = HISTORY_START;
        Instant flushEnd = HISTORY_START.plus(Duration.ofMinutes(5));
        TimeRange reseedRange1 = runCleanupAndCaptureReseedRange(user, device, flushStart, flushEnd);

        // no prior points -> no backward extension; forward only up to 50 points of context,
        // not the static +24h of the old lookback
        assertEquals(flushStart, reseedRange1.start());
        assertEquals(flushEnd.plus(Duration.ofMinutes(250)), reseedRange1.end());

        reseedFromView(user, reseedRange1);

        // 51 timeline points were seeded (50 points of context + the flush), not 289
        assertEquals(51, timelineCount(user));
        assertEquals(51, unprocessedCount(user));

        // the pipeline finished - all points are processed now
        markAllProcessed(user);
        assertEquals(51, processedCount(user));

        // --- promotion cycle 2: a single new point arrives ---
        Instant newPoint = HISTORY_END.plus(Duration.ofMinutes(1));
        assertEquals(1, sourceLocationPointJdbcService.bulkInsert(user, device, List.of(point(newPoint))));

        reset(jobSchedulingService);
        TimeRange reseedRange2 = runCleanupAndCaptureReseedRange(user, device, newPoint, newPoint);
        reseedFromView(user, reseedRange2);

        // the single new point re-opens only the 50-point context margin before it,
        // not the ~48h window the old lookback produced
        assertEquals(HISTORY_END.minus(Duration.ofMinutes(49 * 5)), reseedRange2.start());
        assertEquals(51, processedCount(user), "already processed points must stay processed");
        assertEquals(51, unprocessedCount(user), "50 context points + the new point");
        assertEquals(102, timelineCount(user));
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
