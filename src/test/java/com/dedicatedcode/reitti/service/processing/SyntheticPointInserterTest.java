package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.processing.DetectionParameter;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.service.VisitDetectionParametersService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.ZoneOffset;
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
    private VisitDetectionParametersService detectionParamsService;

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
        // Determine the time range of new points (just one point)
        TimeRange range = new TimeRange(newPointTime, newPointTime);
        syntheticPointInserter.fillGaps(testUser, range);

        // Then: synthetic points should be inserted
        List<RawLocationPoint> all = rawLocationPointService
                .findByUserAndTimestampBetweenOrderByTimestampAsc(testUser,
                        start.minus(1, ChronoUnit.MINUTES), end.plus(1, ChronoUnit.MINUTES));
        long syntheticCount = all.stream().filter(RawLocationPoint::isSynthetic).count();
        assertTrue(syntheticCount > 0, "Should have generated synthetic points");
    }

    @Test
    void shouldRespectMaxInterpolationDistance() {
        Instant start = Instant.parse("2023-01-01T10:00:00Z");
        Instant end = start.plus(2, ChronoUnit.MINUTES);
        // Two points ~1.4km apart → distance > 500m default
        createAndSaveRawPoint(start, 50.0, 8.0);
        createAndSaveRawPoint(end, 50.01, 8.01);

        TimeRange range = new TimeRange(start.plus(1, ChronoUnit.MINUTES), start.plus(1, ChronoUnit.MINUTES));
        syntheticPointInserter.fillGaps(testUser, range);

        List<RawLocationPoint> all = rawLocationPointService
                .findByUserAndTimestampBetweenOrderByTimestampAsc(testUser,
                        start.minus(1, ChronoUnit.MINUTES), end.plus(1, ChronoUnit.MINUTES));
        assertEquals(0, all.stream().filter(RawLocationPoint::isSynthetic).count(),
                "No synthetic points for large distances");
    }

    @Test
    void shouldRespectMaxInterpolationTimeGap() {
        Instant start = Instant.parse("2023-01-01T10:00:00Z");
        Instant end = start.plus(3, ChronoUnit.HOURS); // 180 min > 120 min gap limit

        createAndSaveRawPoint(start, 50.0, 8.0);
        createAndSaveRawPoint(end, 50.001, 8.001);

        TimeRange range = new TimeRange(start.plus(90, ChronoUnit.MINUTES), start.plus(90, ChronoUnit.MINUTES));
        syntheticPointInserter.fillGaps(testUser, range);

        List<RawLocationPoint> all = rawLocationPointService
                .findByUserAndTimestampBetweenOrderByTimestampAsc(testUser,
                        start.minus(1, ChronoUnit.MINUTES), end.plus(1, ChronoUnit.MINUTES));
        assertEquals(0, all.stream().filter(RawLocationPoint::isSynthetic).count(),
                "No synthetic points for large time gaps");
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

    // Optional composite test adapted from the original “shouldNotHaveIgnoredSyntheticPoints”
    @Test
    void shouldGenerateExpectedNumberOfSyntheticPointsForGivenRealPoints() {
        Instant start = Instant.parse("2013-04-15T06:31:26.860000Z");
        // original series of points with ~1 min gaps
        createAndSaveRawPoint(Instant.parse("2013-04-15T06:31:26.860000Z"), 50.0, 8.0);
        createAndSaveRawPoint(Instant.parse("2013-04-15T06:32:31.475000Z"), 50.0, 8.0);
        createAndSaveRawPoint(Instant.parse("2013-04-15T06:33:32.406000Z"), 50.0, 8.0);
        createAndSaveRawPoint(Instant.parse("2013-04-15T06:34:32.478000Z"), 50.0, 8.0);
        createAndSaveRawPoint(Instant.parse("2013-04-15T06:35:32.492000Z"), 50.0, 8.0);
        createAndSaveRawPoint(Instant.parse("2013-04-15T06:36:32.566000Z"), 50.0, 8.0);

        TimeRange range = new TimeRange(
                Instant.parse("2013-04-15T06:31:26.860000Z"),
                Instant.parse("2013-04-15T06:36:32.566000Z"));
        syntheticPointInserter.fillGaps(testUser, range);

        List<RawLocationPoint> stored = rawLocationPointService
                .findByUserAndProcessedIsFalseOrderByTimestampWithLimit(testUser, 1000, 0);
        assertEquals(26, stored.size(), "Total points should be 26 (6 real + 20 synthetic)");
        assertEquals(20, stored.stream().filter(RawLocationPoint::isSynthetic).count(),
                "Exactly 20 synthetic points");
    }

    // --------- helpers ----------
    private RawLocationPoint createAndSaveRawPoint(Instant timestamp, double lat, double lon) {
        RawLocationPoint point = new RawLocationPoint(
                null, timestamp, new GeoPoint(lat, lon), 10.0, 100.0, false, false, false, false, 1L
        );
        return rawLocationPointService.create(testUser, point);
    }
}