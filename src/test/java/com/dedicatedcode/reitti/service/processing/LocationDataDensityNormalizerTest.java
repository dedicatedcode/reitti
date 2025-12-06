package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class LocationDataDensityNormalizerTest {

    @Autowired
    private LocationDataDensityNormalizer normalizer;

    @Autowired
    private RawLocationPointJdbcService rawLocationPointService;

    @Autowired
    private TestingService testingService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testingService.clearData();
        testUser = testingService.randomUser();
    }

    @Test
    void shouldNotHaveIgnoredSyntheticPoints() {
        List<RawLocationPoint> points = new ArrayList<>();
        points.add(createAndSaveRawPoint(Instant.parse("2013-04-15T06:31:26.860000Z"), 50.0, 8.0));
        points.add(createAndSaveRawPoint(Instant.parse("2013-04-15T06:32:31.475000Z"), 50.0, 8.0));
        points.add(createAndSaveRawPoint(Instant.parse("2013-04-15T06:33:32.406000Z"), 50.0, 8.0));
        points.add(createAndSaveRawPoint(Instant.parse("2013-04-15T06:34:32.478000Z"), 50.0, 8.0));
        points.add(createAndSaveRawPoint(Instant.parse("2013-04-15T06:35:32.492000Z"), 50.0, 8.0));
        points.add(createAndSaveRawPoint(Instant.parse("2013-04-15T06:36:32.566000Z"), 50.0, 8.0));

        normalizer.normalize(testUser, points.stream().map(rlp -> {
            LocationPoint locationPoint = new LocationPoint();
            locationPoint.setLatitude(rlp.getLatitude());
            locationPoint.setLongitude(rlp.getLongitude());
            locationPoint.setTimestamp(rlp.getTimestamp().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            locationPoint.setAccuracyMeters(10.0);
            locationPoint.setElevationMeters(100.0);
            return locationPoint;
        }).toList());

        List<RawLocationPoint> storedPoints = this.rawLocationPointService.findByUserAndProcessedIsFalseOrderByTimestampWithLimit(testUser, 1000, 0);


        assertEquals(8, storedPoints.size());
        assertEquals(0, storedPoints.stream().filter(RawLocationPoint::isIgnored).count());
        assertEquals(2, storedPoints.stream().filter(RawLocationPoint::isSynthetic).count());
    }

    @Test
    void shouldGenerateSyntheticPointsForLargeGaps() {
        // Given: Create two points with a 2-minute gap
        Instant startTime = Instant.parse("2023-01-01T10:00:00Z");
        Instant endTime = startTime.plus(2, ChronoUnit.MINUTES);

        createAndSaveRawPoint(startTime, 50.0, 8.0);
        createAndSaveRawPoint(endTime, 50.0001, 8.0001);

        // When: Normalize around a new point in between
        LocationPoint newPoint = createLocationPoint(startTime.plus(1, ChronoUnit.MINUTES), 50.0005, 8.0005);
        normalizer.normalize(testUser, Collections.singletonList(newPoint));

        // Then: Should have generated synthetic points to fill the gaps
        List<RawLocationPoint> allPoints = rawLocationPointService.findByUserAndTimestampBetweenOrderByTimestampAsc(
            testUser, startTime.minus(1, ChronoUnit.MINUTES), endTime.plus(1, ChronoUnit.MINUTES)
        );

        // Should have original 2 points + new point + synthetic points
        assertTrue(allPoints.size() > 3, "Should have generated synthetic points");
        
        // Count synthetic points
        long syntheticCount = allPoints.stream().filter(RawLocationPoint::isSynthetic).count();
        assertTrue(syntheticCount > 0, "Should have synthetic points");
    }

    @Test
    void shouldMarkExcessPointsAsIgnored() {
        // Given: Create multiple points very close together in time (within tolerance)
        Instant baseTime = Instant.parse("2023-01-01T10:00:00Z");
        
        createAndSaveRawPoint(baseTime, 50.0, 8.0);
        createAndSaveRawPoint(baseTime.plus(5, ChronoUnit.SECONDS), 50.0001, 8.0001); // Too close
        createAndSaveRawPoint(baseTime.plus(10, ChronoUnit.SECONDS), 50.0002, 8.0002); // Too close

        // When: Normalize around a new point
        LocationPoint newPoint = createLocationPoint(baseTime.plus(7, ChronoUnit.SECONDS), 50.00015, 8.00015);
        normalizer.normalize(testUser, Collections.singletonList(newPoint));

        // Then: Some points should be marked as ignored
        List<RawLocationPoint> allPoints = rawLocationPointService.findByUserAndTimestampBetweenOrderByTimestampAsc(
            testUser, baseTime.minus(1, ChronoUnit.MINUTES), baseTime.plus(1, ChronoUnit.MINUTES)
        );

        long ignoredCount = allPoints.stream().filter(RawLocationPoint::isIgnored).count();
        assertTrue(ignoredCount > 0, "Should have marked some points as ignored");
    }

    @Test
    void shouldRespectMaxInterpolationDistance() {
        // Given: Create two points very far apart
        Instant startTime = Instant.parse("2023-01-01T10:00:00Z");
        Instant endTime = startTime.plus(2, ChronoUnit.MINUTES);

        createAndSaveRawPoint(startTime, 50.0, 8.0);
        createAndSaveRawPoint(endTime, 50.01, 8.01); // ~1.4km apart

        // When: Normalize around a new point (with default 500m max distance)
        LocationPoint newPoint = createLocationPoint(startTime.plus(1, ChronoUnit.MINUTES), 50.005, 8.005);
        normalizer.normalize(testUser, Collections.singletonList(newPoint));

        // Then: Should not generate synthetic points due to distance constraint
        List<RawLocationPoint> allPoints = rawLocationPointService.findByUserAndTimestampBetweenOrderByTimestampAsc(
            testUser, startTime.minus(1, ChronoUnit.MINUTES), endTime.plus(1, ChronoUnit.MINUTES)
        );

        long syntheticCount = allPoints.stream().filter(RawLocationPoint::isSynthetic).count();
        assertEquals(0, syntheticCount, "Should not generate synthetic points for large distances");
    }

    @Test
    void shouldRespectMaxInterpolationTimeGap() {
        // Given: Create two points with a very large time gap
        Instant startTime = Instant.parse("2023-01-01T10:00:00Z");
        Instant endTime = startTime.plus(3, ChronoUnit.HOURS); // 3 hours apart (> default 120 minutes)

        createAndSaveRawPoint(startTime, 50.0, 8.0);
        createAndSaveRawPoint(endTime, 50.001, 8.001);

        // When: Normalize around a new point
        LocationPoint newPoint = createLocationPoint(startTime.plus(90, ChronoUnit.MINUTES), 50.0005, 8.0005);
        normalizer.normalize(testUser, Collections.singletonList(newPoint));

        // Then: Should not generate synthetic points due to time gap constraint
        List<RawLocationPoint> allPoints = rawLocationPointService.findByUserAndTimestampBetweenOrderByTimestampAsc(
            testUser, startTime.minus(1, ChronoUnit.MINUTES), endTime.plus(1, ChronoUnit.MINUTES)
        );

        long syntheticCount = allPoints.stream().filter(RawLocationPoint::isSynthetic).count();
        assertEquals(0, syntheticCount, "Should not generate synthetic points for large time gaps");
    }

    @Test
    void shouldHandleEmptyDataGracefully() {
        // Given: No existing points
        
        // When: Normalize around a new point
        LocationPoint newPoint = createLocationPoint(Instant.parse("2023-01-01T10:00:00Z"), 50.0, 8.0);
        
        // Then: Should not throw exception
        assertDoesNotThrow(() -> normalizer.normalize(testUser, Collections.singletonList(newPoint)));
    }

    @Test
    void shouldHandleSinglePointGracefully() {
        // Given: Only one existing point
        createAndSaveRawPoint(Instant.parse("2023-01-01T10:00:00Z"), 50.0, 8.0);
        
        // When: Normalize around a new point
        LocationPoint newPoint = createLocationPoint(Instant.parse("2023-01-01T10:01:00Z"), 50.001, 8.001);
        
        // Then: Should not throw exception
        assertDoesNotThrow(() -> normalizer.normalize(testUser, Collections.singletonList(newPoint)));
    }

    private RawLocationPoint createAndSaveRawPoint(Instant timestamp, double lat, double lon) {
        RawLocationPoint point = new RawLocationPoint(
            null, timestamp, new GeoPoint(lat, lon), 10.0, 100.0, false, false, false, 1L
        );
        return rawLocationPointService.create(testUser, point);
    }

    private LocationPoint createLocationPoint(Instant timestamp, double lat, double lon) {
        LocationPoint point = new LocationPoint();
        point.setLatitude(lat);
        point.setLongitude(lon);
        point.setTimestamp(timestamp.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        point.setAccuracyMeters(10.0);
        point.setElevationMeters(100.0);
        return point;
    }
}
