package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.processing.LocationPointStagingService.PromotionResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the #1212 fix: promote() returns the exact time range of the rows it actually
 * inserted. A promotion that races with concurrent ingest (or a duplicate queued promotion
 * job that finds no unpromoted points) therefore schedules its cleanup job with the range of
 * the few new points only - not with the whole partition range as the previous fallback
 * (getWholeTimeRange) did, which re-opened the whole day for reprocessing.
 */
@IntegrationTest
class LocationPointStagingServiceTest {

    private static final Instant MORNING_START = Instant.parse("2026-08-27T06:00:00Z");
    private static final Instant MORNING_END = Instant.parse("2026-08-27T06:05:00Z");
    private static final Instant EVENING_FLUSH = Instant.parse("2026-08-27T23:59:55Z");

    @Autowired
    private LocationPointStagingService stagingService;
    @Autowired
    private TestingService testingService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void promotedRangeCoversOnlyTheActuallyInsertedRows() {
        User user = testingService.randomUser();
        Device device = testingService.findDefaultDevice(user);
        String partitionKey = "stream_" + user.getId() + "_" + device.id() + "_20260827";

        stagingService.ensurePartitionExists(partitionKey);

        // first flush of the day, promoted in the morning
        stagingService.insertBatch(partitionKey, user, device, points(MORNING_START, MORNING_END, Duration.ofSeconds(30)));
        PromotionResult firstPromotion = stagingService.promote(user, partitionKey);
        assertEquals(11, firstPromotion.promotedCount());
        assertEquals(MORNING_START, firstPromotion.promotedRange().start());
        assertEquals(MORNING_END, firstPromotion.promotedRange().end());

        // 18 hours later a duplicate promotion job for the same partition runs: nothing to do
        assertFalse(stagingService.hasUnpromotedPoints(partitionKey));
        PromotionResult emptyPromotion = stagingService.promote(user, partitionKey);
        assertEquals(0, emptyPromotion.promotedCount());
        assertFalse(emptyPromotion.hasPromoted());

        // ... meanwhile the ingest thread stages a single new point
        stagingService.insertBatch(partitionKey, user, device, List.of(point(EVENING_FLUSH)));
        assertTrue(stagingService.hasUnpromotedPoints(partitionKey));

        // the promotion picks it up and returns exactly its range - not the whole partition
        PromotionResult result = stagingService.promote(user, partitionKey);
        assertEquals(1, result.promotedCount());
        assertEquals(EVENING_FLUSH, result.promotedRange().start());
        assertEquals(EVENING_FLUSH, result.promotedRange().end());
        assertFalse(stagingService.hasUnpromotedPoints(partitionKey));

        // and the promoted rows are still sitting in staging - they are never deleted in live mode
        String tableName = "staged_" + partitionKey.toLowerCase().replace("-", "_").replace(".", "_");
        Long rowsInStaging = jdbcTemplate.queryForObject("SELECT count(*) FROM " + tableName + " WHERE partition_key = ?", Long.class, partitionKey);
        assertEquals(12, rowsInStaging);
    }

    @Test
    void concurrentFlushDuringPromotionIsNotLost() {
        User user = testingService.randomUser();
        Device device = testingService.findDefaultDevice(user);
        String partitionKey = "stream_" + user.getId() + "_" + device.id() + "_20260827";

        stagingService.ensurePartitionExists(partitionKey);

        // the promotion claims and inserts all unpromoted rows atomically ...
        stagingService.insertBatch(partitionKey, user, device, List.of(point(MORNING_START)));
        PromotionResult first = stagingService.promote(user, partitionKey);
        assertEquals(1, first.promotedCount());

        // ... so a point inserted afterwards stays unpromoted and is picked up by the next
        // promotion instead of being marked promoted without ever reaching raw_source_points
        stagingService.insertBatch(partitionKey, user, device, List.of(point(EVENING_FLUSH)));
        assertTrue(stagingService.hasUnpromotedPoints(partitionKey));

        PromotionResult second = stagingService.promote(user, partitionKey);
        assertEquals(1, second.promotedCount());
        assertEquals(EVENING_FLUSH, second.promotedRange().start());
        assertFalse(stagingService.hasUnpromotedPoints(partitionKey));
    }

    @Test
    void promoteWithManyRowsReturnsCorrectAggregateAndDeliversAllIds() {
        User user = testingService.randomUser();
        Device device = testingService.findDefaultDevice(user);
        String partitionKey = "stream_" + user.getId() + "_" + device.id() + "_20260827";

        stagingService.ensurePartitionExists(partitionKey);

        // Insert a larger batch that exercises the streaming path
        Instant start = Instant.parse("2026-08-27T00:00:00Z");
        int pointCount = 500;
        List<LocationPoint> batch = new ArrayList<>();
        for (int i = 0; i < pointCount; i++) {
            batch.add(point(start.plusSeconds(i * 60)));
        }
        stagingService.insertBatch(partitionKey, user, device, batch);

        PromotionResult result = stagingService.promote(user, partitionKey);
        assertEquals(pointCount, result.promotedCount());
        assertNotNull(result.promotedRange());
        assertEquals(start, result.promotedRange().start());
        assertEquals(start.plusSeconds((pointCount - 1) * 60), result.promotedRange().end());
        assertTrue(result.hasPromoted());

        // Verify all rows actually landed in raw_source_points
        Long rawCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM raw_source_points WHERE user_id = ? AND device_id = ?",
                Long.class, user.getId(), device.id());
        assertEquals(pointCount, rawCount);

        // Promoting again yields empty
        PromotionResult empty = stagingService.promote(user, partitionKey);
        assertEquals(0, empty.promotedCount());
        assertFalse(empty.hasPromoted());
    }

    @Test
    void promoteEmptyPartitionReturnsEmptyResult() {
        User user = testingService.randomUser();
        Device device = testingService.findDefaultDevice(user);
        String partitionKey = "stream_" + user.getId() + "_" + device.id() + "_20260827";

        stagingService.ensurePartitionExists(partitionKey);

        PromotionResult result = stagingService.promote(user, partitionKey);
        assertEquals(0, result.promotedCount());
        assertFalse(result.hasPromoted());
        assertEquals(TimeRange.empty(), result.promotedRange());
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
