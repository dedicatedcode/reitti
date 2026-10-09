package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.config.LocationDensityConfig;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.GeoUtils;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class SyntheticPointInserterTest {

    @Autowired
    private SyntheticPointInserter syntheticPointInserter;

    @Autowired
    private RawLocationPointJdbcService rawLocationPointService;

    @Autowired
    private TestingService testingService;

    @Autowired
    private LocationDensityConfig locationDensityConfig;

    private User testUser;

    @BeforeEach
    void setUp() {
        testingService.clearData();
        testUser = testingService.randomUser();
    }

    @Test
    void shouldGenerateSyntheticPointsForLargeGaps() {
        // Given: two real points with a 2-minute gap
        Instant start = Instant.parse("2023-01-01T10:00:00Z");
        Instant end = start.plus(2, ChronoUnit.MINUTES);
        createAndSaveRawPoint(start, 50.0, 8.0);
        createAndSaveRawPoint(end, 50.0001, 8.0001);

        // When: we simulate a new point arriving in between and trigger gap filling
        Instant newPointTime = start.plus(1, ChronoUnit.MINUTES);
        // the caller passes a window covering the gap (see ProcessingWindowResolver)
        TimeRange range = new TimeRange(start, end.plusMillis(1));
        syntheticPointInserter.fillGaps(testUser, range);

        // Then: synthetic points should be inserted
        List<RawLocationPoint> all = rawLocationPointService
                .findByUserAndTimestampBetweenOrderByTimestampAsc(testUser,
                        start.minus(1, ChronoUnit.MINUTES), end.plus(1, ChronoUnit.MINUTES));
        long syntheticCount = all.stream().filter(RawLocationPoint::isSynthetic).count();
        assertTrue(syntheticCount > 0, "Should have generated synthetic points");
    }

    @Test
    void shouldInterpolateShortFastGaps() {
        Instant start = Instant.parse("2023-01-01T10:00:00Z");
        Instant end = start.plus(2, ChronoUnit.MINUTES);
        // Two points ~1.4km apart -> ~40 km/h, far above the stationary bound. The gap is shorter
        // than the minimum stay time, so a straight line between the endpoints is honest.
        createAndSaveRawPoint(start, 50.0, 8.0);
        createAndSaveRawPoint(end, 50.01, 8.01);

        TimeRange range = new TimeRange(start, end.plusMillis(1));
        syntheticPointInserter.fillGaps(testUser, range);

        List<RawLocationPoint> all = rawLocationPointService
                .findByUserAndTimestampBetweenOrderByTimestampAsc(testUser,
                        start.minus(1, ChronoUnit.MINUTES), end.plus(1, ChronoUnit.MINUTES));
        List<RawLocationPoint> synthetic = all.stream().filter(RawLocationPoint::isSynthetic).toList();
        assertFalse(synthetic.isEmpty(), "Short gaps above the stationary speed bound should be interpolated");
    }

    @Test
    void shouldFillLongDistanceGapsWithStationaryCluster() {
        Instant start = Instant.parse("2023-01-01T10:00:00Z");
        Instant end = start.plus(3, ChronoUnit.HOURS);

        // Two points ~660m apart over 3h -> 0.22 km/h, far below the stationary bound
        createAndSaveRawPoint(start, 50.0, 8.0);
        createAndSaveRawPoint(end, 50.005, 8.005);

        TimeRange range = new TimeRange(start, end.plusMillis(1));
        syntheticPointInserter.fillGaps(testUser, range);

        List<RawLocationPoint> all = rawLocationPointService
                .findByUserAndTimestampBetweenOrderByTimestampAsc(testUser,
                        start.minus(1, ChronoUnit.MINUTES), end.plus(1, ChronoUnit.MINUTES));
        List<RawLocationPoint> synthetic = all.stream().filter(RawLocationPoint::isSynthetic).toList();
        assertFalse(synthetic.isEmpty(), "Long gaps below the stationary speed bound should be filled with a stationary cluster");

        for (RawLocationPoint point : synthetic) {
            double distance = GeoUtils.distanceInMeters(point.getGeom(), new GeoPoint(50.0, 8.0));
            assertTrue(distance <= 50.0, "Stationary synthetic point must stay near the anchor point, was " + distance + "m");
        }
    }

    @Test
    void shouldFillBatterySavingGapAsSingleContiguousRun() {
        // The case this feature exists for: a device that only reports on movement, so a 6-hour
        // stay at home arrives as two points 300m apart. 300m over 6h is 0.05 km/h.
        Instant start = Instant.parse("2023-01-01T10:00:00Z");
        Instant end = start.plus(6, ChronoUnit.HOURS);

        createAndSaveRawPoint(start, 50.0, 8.0);
        createAndSaveRawPoint(end, 50.0027, 8.0017);

        TimeRange range = new TimeRange(start, end.plusMillis(1));
        syntheticPointInserter.fillGaps(testUser, range);

        List<RawLocationPoint> synthetic = rawLocationPointService
                .findByUserAndTimestampBetweenOrderByTimestampAsc(testUser, start, end)
                .stream().filter(RawLocationPoint::isSynthetic).toList();

        assertFalse(synthetic.isEmpty(), "a 6h / 300m gap should be filled as stationary");
        // the fill spans the whole gap, ending one sampling interval short of the last real point
        assertEquals(end.minusSeconds(15), synthetic.getLast().getTimestamp(),
                "stationary fill should reach the last real point");

        // and it is one contiguous run: no gap inside it larger than the sampling interval
        for (int i = 1; i < synthetic.size(); i++) {
            long between = Duration.between(synthetic.get(i - 1).getTimestamp(), synthetic.get(i).getTimestamp()).getSeconds();
            assertTrue(between <= 16, "hole of " + between + "s inside the stationary fill would split the visit");
        }
    }

    @Test
    void shouldNotFillGapsBeyondTheInterpolationCeiling() {
        // 14h covering 40km is ~2.6 km/h: movement, but the gap is longer than the 12h
        // interpolation ceiling, so we decline to draw a straight line across it
        Instant start = Instant.parse("2023-01-01T10:00:00Z");
        Instant end = start.plus(14, ChronoUnit.HOURS);

        createAndSaveRawPoint(start, 50.0, 8.0);
        createAndSaveRawPoint(end, 50.36, 8.36);

        TimeRange range = new TimeRange(start, end.plusMillis(1));
        syntheticPointInserter.fillGaps(testUser, range);

        List<RawLocationPoint> all = rawLocationPointService
                .findByUserAndTimestampBetweenOrderByTimestampAsc(testUser,
                        start.minus(1, ChronoUnit.MINUTES), end.plus(1, ChronoUnit.MINUTES));
        assertEquals(0, all.stream().filter(RawLocationPoint::isSynthetic).count(),
                "Gaps beyond the interpolation ceiling stay unfilled");
    }

    @Test
    void shouldFillMovementGapsUpToTheInterpolationCeiling() {
        // 3h covering 30km is ~10 km/h: movement well above the stationary bound, but inside the
        // 12h ceiling, so it is filled as interpolated travel rather than left as a hole
        Instant start = Instant.parse("2023-01-01T10:00:00Z");
        Instant end = start.plus(3, ChronoUnit.HOURS);

        createAndSaveRawPoint(start, 50.0, 8.0);
        createAndSaveRawPoint(end, 50.25, 8.25);

        TimeRange range = new TimeRange(start, end.plusMillis(1));
        syntheticPointInserter.fillGaps(testUser, range);

        List<RawLocationPoint> all = rawLocationPointService
                .findByUserAndTimestampBetweenOrderByTimestampAsc(testUser,
                        start.minus(1, ChronoUnit.MINUTES), end.plus(1, ChronoUnit.MINUTES));
        List<RawLocationPoint> synthetic = all.stream().filter(RawLocationPoint::isSynthetic).toList();
        assertFalse(synthetic.isEmpty(), "Movement gaps inside the interpolation ceiling should be filled");
    }

    @Test
    void shouldInterpolateMovementGapsJustUnderTheCeiling() {
        // just inside the 12h ceiling: ~5 km/h, clearly movement, but interpolatable
        Instant start = Instant.parse("2023-01-01T10:00:00Z");
        Instant end = start.plus(11, ChronoUnit.HOURS);

        createAndSaveRawPoint(start, 50.0, 8.0);
        createAndSaveRawPoint(end, 50.5, 8.5);

        TimeRange range = new TimeRange(start, end.plusMillis(1));
        syntheticPointInserter.fillGaps(testUser, range);

        List<RawLocationPoint> synthetic = rawLocationPointService
                .findByUserAndTimestampBetweenOrderByTimestampAsc(testUser, start, end)
                .stream().filter(RawLocationPoint::isSynthetic).toList();
        assertFalse(synthetic.isEmpty(), "gaps just under the ceiling should interpolate");
    }

    @Test
    void shouldFillShortMovementGapsWithInterpolation() {
        Instant start = Instant.parse("2023-01-01T10:00:00Z");
        Instant end = start.plus(14, ChronoUnit.MINUTES);

        // ~1.4km over 14min -> ~5.7 km/h, above the stationary bound, so this is movement and gets
        // interpolated rather than read as a stay
        createAndSaveRawPoint(start, 50.0, 8.0);
        createAndSaveRawPoint(end, 50.01, 8.01);

        TimeRange range = new TimeRange(start, end.plusMillis(1));
        syntheticPointInserter.fillGaps(testUser, range);

        List<RawLocationPoint> all = rawLocationPointService
                .findByUserAndTimestampBetweenOrderByTimestampAsc(testUser,
                        start.minus(1, ChronoUnit.MINUTES), end.plus(1, ChronoUnit.MINUTES));
        assertTrue(all.stream().anyMatch(RawLocationPoint::isSynthetic),
                "Short movement gaps should be interpolated, not left empty");
    }

    @Test
    void shouldHandleEmptyDataGracefully() {
        // No existing points
        TimeRange range = new TimeRange(Instant.parse("2023-01-01T10:00:00Z"), Instant.parse("2023-01-01T10:00:00Z"));
        assertDoesNotThrow(() -> syntheticPointInserter.fillGaps(testUser, range));
    }

    @Test
    void shouldHandleSinglePointGracefully() {
        createAndSaveRawPoint(Instant.parse("2023-01-01T10:00:00Z"), 50.0, 8.0);
        TimeRange range = new TimeRange(Instant.parse("2023-01-01T10:01:00Z"), Instant.parse("2023-01-01T10:01:00Z"));
        assertDoesNotThrow(() -> syntheticPointInserter.fillGaps(testUser, range));
    }

    @Test
    void shouldGenerateExpectedNumberOfSyntheticPointsForGivenRealPoints() {
        createAndSaveRawPoint(Instant.parse("2013-04-15T06:31:26.860000Z"), 50.0, 8.0);
        createAndSaveRawPoint(Instant.parse("2013-04-15T06:32:31.475000Z"), 50.0, 8.0);
        createAndSaveRawPoint(Instant.parse("2013-04-15T06:33:32.406000Z"), 50.0, 8.0);
        createAndSaveRawPoint(Instant.parse("2013-04-15T06:34:32.478000Z"), 50.0, 8.0);
        createAndSaveRawPoint(Instant.parse("2013-04-15T06:35:32.492000Z"), 50.0, 8.0);
        createAndSaveRawPoint(Instant.parse("2013-04-15T06:36:32.566000Z"), 50.0, 8.0);

        TimeRange range = new TimeRange(
                Instant.parse("2013-04-15T06:31:26.860000Z"),
                Instant.parse("2013-04-15T06:36:32.566000Z").plusMillis(1));
        syntheticPointInserter.fillGaps(testUser, range);

        List<RawLocationPoint> stored = rawLocationPointService
                .findByUserAndProcessedIsFalseOrderByTimestampWithLimit(testUser, 1000, 0);
        assertEquals(26, stored.size(), "Total points should be 26 (6 real + 20 synthetic)");
        assertEquals(20, stored.stream().filter(RawLocationPoint::isSynthetic).count(),
                "Exactly 20 synthetic points");
    }

    @Test
    void shouldDefaultStationarySpeedToOneKmh() {
        assertEquals(1.0, locationDensityConfig.getMaxStationarySpeedKmh(), 0.0001,
                "default stationary speed bound");
        assertEquals(1.0 / 3.6, locationDensityConfig.getMaxStationarySpeedMps(), 0.0001,
                "bound converted to metres per second");
    }

    @Test
    void shouldDefaultInterpolationCeilingToTwelveHours() {
        assertEquals(12, locationDensityConfig.getMaxInterpolationGapHours(),
                "default ceiling for interpolating movement across a gap");
    }

    @Test
    void shouldClassifyOnTheStationarySpeedBound() {
        // The single decision the feature turns on: implied speed against the bound, no distance cap.
        double bound = locationDensityConfig.getMaxStationarySpeedMps();

        // 300m across 6h — a parked device under battery saving
        assertTrue(300.0 / (6 * 3600) <= bound, "battery-saving stay should be stationary");

        // 30km across 3h — a drive with only two reports
        assertFalse(30000.0 / (3 * 3600) <= bound, "a drive should not be stationary");

        // 1.4km across 14min — well above the bound
        assertFalse(1400.0 / (14 * 60) <= bound, "a 6 km/h gap should not be stationary");
    }

    // --------- helpers ----------
    private void createAndSaveRawPoint(Instant timestamp, double lat, double lon) {
        RawLocationPoint point = new RawLocationPoint(
                null, null, timestamp, new GeoPoint(lat, lon), 10.0, 100.0, false, false, 1L
        );
        rawLocationPointService.create(testUser, point);
    }
}