package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SyntheticLocationPointGeneratorTest {

    private SyntheticLocationPointGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SyntheticLocationPointGenerator();
    }

    @Test
    void shouldGenerateSyntheticPointsForValidGap() {
        // Given: Two points 2 minutes apart (120 seconds)
        Instant startTime = Instant.parse("2023-01-01T10:00:00Z");
        Instant endTime = startTime.plus(2, ChronoUnit.MINUTES);
        
        RawLocationPoint startPoint = new RawLocationPoint(
            1L, startTime, new GeoPoint(50.0, 8.0), 10.0, 100.0, false, false, false, 1L
        );
        RawLocationPoint endPoint = new RawLocationPoint(
            2L, endTime, new GeoPoint(50.001, 8.001), 15.0, 105.0, false, false, false, 1L
        );

        // When: Generate synthetic points for 4 points per minute (15 second intervals)
        List<LocationPoint> syntheticPoints = generator.generateSyntheticPoints(
            startPoint, endPoint, 4, 500.0
        );

        // Then: Should generate 7 points (at 15, 30, 45, 60, 75, 90, 105 seconds)
        assertEquals(7, syntheticPoints.size());
        
        // Verify first synthetic point
        LocationPoint firstPoint = syntheticPoints.get(0);
        assertEquals("2023-01-01T10:00:15Z", firstPoint.getTimestamp());
        assertTrue(firstPoint.getLatitude() > 50.0 && firstPoint.getLatitude() < 50.001);
        assertTrue(firstPoint.getLongitude() > 8.0 && firstPoint.getLongitude() < 8.001);
        
        // Verify last synthetic point
        LocationPoint lastPoint = syntheticPoints.get(6);
        assertEquals("2023-01-01T10:01:45Z", lastPoint.getTimestamp());
    }

    @Test
    void shouldInterpolateCoordinatesCorrectly() {
        // Given: Two points with known coordinates
        Instant startTime = Instant.parse("2023-01-01T10:00:00Z");
        Instant endTime = startTime.plus(1, ChronoUnit.MINUTES);
        
        RawLocationPoint startPoint = new RawLocationPoint(
            1L, startTime, new GeoPoint(50.0, 8.0), 10.0, 100.0, false, false, false, 1L
        );
        RawLocationPoint endPoint = new RawLocationPoint(
            2L, endTime, new GeoPoint(50.002, 8.002), 20.0, 110.0, false, false, false, 1L
        );

        // When: Generate synthetic points
        List<LocationPoint> syntheticPoints = generator.generateSyntheticPoints(
            startPoint, endPoint, 4, 500.0
        );

        // Then: Should generate 3 points (at 15, 30, 45 seconds)
        assertEquals(3, syntheticPoints.size());
        
        // Verify middle point coordinates (should be halfway between start and end)
        LocationPoint middlePoint = syntheticPoints.get(1); // 30 seconds = 50% of the way
        assertEquals(50.0012, middlePoint.getLatitude(), 0.0001);
        assertEquals(8.001, middlePoint.getLongitude(), 0.001);
    }

    @Test
    void shouldInterpolateAccuracyAndElevation() {
        // Given: Two points with different accuracy and elevation
        Instant startTime = Instant.parse("2023-01-01T10:00:00Z");
        Instant endTime = startTime.plus(1, ChronoUnit.MINUTES);
        
        RawLocationPoint startPoint = new RawLocationPoint(
            1L, startTime, new GeoPoint(50.0, 8.0), 10.0, 100.0, false, false, false, 1L
        );
        RawLocationPoint endPoint = new RawLocationPoint(
            2L, endTime, new GeoPoint(50.001, 8.001), 20.0, 120.0, false, false, false, 1L
        );

        // When: Generate synthetic points
        List<LocationPoint> syntheticPoints = generator.generateSyntheticPoints(
            startPoint, endPoint, 4, 500.0
        );

        // Then: Middle point should have interpolated values
        LocationPoint middlePoint = syntheticPoints.get(1); // 30 seconds = 50% of the way
        assertEquals(14.785, middlePoint.getAccuracyMeters(), 0.05); // 10 + (20-10) * 0.5
        assertEquals(109.57, middlePoint.getElevationMeters(), 0.02); // 100 + (120-100) * 0.5
    }

    @Test
    void shouldHandleNullAccuracyAndElevation() {
        // Given: Points with null accuracy and elevation
        Instant startTime = Instant.parse("2023-01-01T10:00:00Z");
        Instant endTime = startTime.plus(1, ChronoUnit.MINUTES);
        
        RawLocationPoint startPoint = new RawLocationPoint(
            1L, startTime, new GeoPoint(50.0, 8.0), null, null, false, false, false, 1L
        );
        RawLocationPoint endPoint = new RawLocationPoint(
            2L, endTime, new GeoPoint(50.001, 8.001), null, null, false, false, false, 1L
        );

        // When: Generate synthetic points
        List<LocationPoint> syntheticPoints = generator.generateSyntheticPoints(
            startPoint, endPoint, 4, 500.0
        );

        // Then: Should generate points with null accuracy and elevation
        assertEquals(3, syntheticPoints.size());
        LocationPoint point = syntheticPoints.get(0);
        assertNull(point.getAccuracyMeters());
        assertNull(point.getElevationMeters());
    }

    @Test
    void shouldNotInterpolateWhenDistanceTooLarge() {
        // Given: Two points very far apart (> 500m)
        Instant startTime = Instant.parse("2023-01-01T10:00:00Z");
        Instant endTime = startTime.plus(1, ChronoUnit.MINUTES);
        
        RawLocationPoint startPoint = new RawLocationPoint(
            1L, startTime, new GeoPoint(50.0, 8.0), 10.0, 100.0, false, false, false, 1L
        );
        RawLocationPoint endPoint = new RawLocationPoint(
            2L, endTime, new GeoPoint(50.01, 8.01), 20.0, 110.0, false, false, false, 1L // ~1.4km apart
        );

        // When: Generate synthetic points with 500m max distance
        List<LocationPoint> syntheticPoints = generator.generateSyntheticPoints(
            startPoint, endPoint, 4, 500.0
        );

        // Then: Should not generate any points
        assertTrue(syntheticPoints.isEmpty());
    }

    @Test
    void shouldNotGeneratePointsForShortGaps() {
        // Given: Two points only 10 seconds apart
        Instant startTime = Instant.parse("2023-01-01T10:00:00Z");
        Instant endTime = startTime.plus(10, ChronoUnit.SECONDS);
        
        RawLocationPoint startPoint = new RawLocationPoint(
            1L, startTime, new GeoPoint(50.0, 8.0), 10.0, 100.0, false, false, false, 1L
        );
        RawLocationPoint endPoint = new RawLocationPoint(
            2L, endTime, new GeoPoint(50.0001, 8.0001), 15.0, 105.0, false, false, false, 1L
        );

        // When: Generate synthetic points for 4 points per minute (15 second intervals)
        List<LocationPoint> syntheticPoints = generator.generateSyntheticPoints(
            startPoint, endPoint, 4, 500.0
        );

        // Then: Should not generate any points (gap too small)
        assertTrue(syntheticPoints.isEmpty());
    }

    @Test
    void shouldGenerateCorrectTimestamps() {
        // Given: Two points 75 seconds apart
        Instant startTime = Instant.parse("2023-01-01T10:00:00Z");
        Instant endTime = startTime.plus(75, ChronoUnit.SECONDS);
        
        RawLocationPoint startPoint = new RawLocationPoint(
            1L, startTime, new GeoPoint(50.0, 8.0), 10.0, 100.0, false, false, false, 1L
        );
        RawLocationPoint endPoint = new RawLocationPoint(
            2L, endTime, new GeoPoint(50.001, 8.001), 15.0, 105.0, false, false, false, 1L
        );

        // When: Generate synthetic points for 4 points per minute (15 second intervals)
        List<LocationPoint> syntheticPoints = generator.generateSyntheticPoints(
            startPoint, endPoint, 4, 500.0
        );

        // Then: Should generate 4 points at 15, 30, 45, 60 seconds
        assertEquals(4, syntheticPoints.size());
        assertEquals("2023-01-01T10:00:15Z", syntheticPoints.get(0).getTimestamp());
        assertEquals("2023-01-01T10:00:30Z", syntheticPoints.get(1).getTimestamp());
        assertEquals("2023-01-01T10:00:45Z", syntheticPoints.get(2).getTimestamp());
        assertEquals("2023-01-01T10:01:00Z", syntheticPoints.get(3).getTimestamp());
    }
}
