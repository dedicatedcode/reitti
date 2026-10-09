package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.GeoUtils;
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
                1L, null, startTime, new GeoPoint(50.0, 8.0), 10.0, 100.0, false, false, 1L
        );
        RawLocationPoint endPoint = new RawLocationPoint(
                2L, null, endTime, new GeoPoint(50.001, 8.001), 15.0, 105.0, false, false, 1L
        );

        // When: Generate synthetic points for 4 points per minute (15 second intervals)
        List<LocationPoint> syntheticPoints = generator.generateSyntheticPoints(
            startPoint, endPoint, 4
        );

        // Then: Should generate 7 points (at 15, 30, 45, 60, 75, 90, 105 seconds)
        assertEquals(7, syntheticPoints.size());
        
        // Verify first synthetic point
        LocationPoint firstPoint = syntheticPoints.get(0);
        assertEquals(Instant.parse("2023-01-01T10:00:15Z"), firstPoint.getTimestamp());
        assertTrue(firstPoint.getLatitude() > 50.0 && firstPoint.getLatitude() < 50.001);
        assertTrue(firstPoint.getLongitude() > 8.0 && firstPoint.getLongitude() < 8.001);
        
        // Verify last synthetic point
        LocationPoint lastPoint = syntheticPoints.get(6);
        assertEquals(Instant.parse("2023-01-01T10:01:45Z"), lastPoint.getTimestamp());
    }

    @Test
    void shouldInterpolateCoordinatesCorrectly() {
        // Given: Two points with known coordinates
        Instant startTime = Instant.parse("2023-01-01T10:00:00Z");
        Instant endTime = startTime.plus(1, ChronoUnit.MINUTES);
        
        RawLocationPoint startPoint = new RawLocationPoint(
                1L, null, startTime, new GeoPoint(50.0, 8.0), 10.0, 100.0, false, false, 1L
        );
        RawLocationPoint endPoint = new RawLocationPoint(
                2L, null, endTime, new GeoPoint(50.002, 8.002), 20.0, 110.0, false, false, 1L
        );

        // When: Generate synthetic points
        List<LocationPoint> syntheticPoints = generator.generateSyntheticPoints(
            startPoint, endPoint, 4
        );

        // Then: Should generate 3 points (at 15, 30, 45 seconds)
        assertEquals(3, syntheticPoints.size());
        
        // Verify middle point coordinates (should be halfway between start and end)
        LocationPoint middlePoint = syntheticPoints.get(1); // 30 seconds = 50% of the way
        assertEquals(50.0009, middlePoint.getLatitude(), 0.0001);
        assertEquals(8.001, middlePoint.getLongitude(), 0.001);
    }

    @Test
    void shouldInterpolateAccuracyAndElevation() {
        // Given: Two points with different accuracy and elevation
        Instant startTime = Instant.parse("2023-01-01T10:00:00Z");
        Instant endTime = startTime.plus(1, ChronoUnit.MINUTES);
        
        RawLocationPoint startPoint = new RawLocationPoint(
                1L, null, startTime, new GeoPoint(50.0, 8.0), 10.0, 100.0, false, false, 1L
        );
        RawLocationPoint endPoint = new RawLocationPoint(
                2L, null, endTime, new GeoPoint(50.001, 8.001), 20.0, 120.0, false, false, 1L
        );

        // When: Generate synthetic points
        List<LocationPoint> syntheticPoints = generator.generateSyntheticPoints(
            startPoint, endPoint, 4
        );

        // Then: Middle point should have interpolated values
        LocationPoint middlePoint = syntheticPoints.get(1); // 30 seconds = 50% of the way
        assertEquals(14.629, middlePoint.getAccuracyMeters(), 0.05); // 10 + (20-10) * 0.5
        assertEquals(109.25, middlePoint.getElevationMeters(), 0.02); // 100 + (120-100) * 0.5
    }

    @Test
    void shouldHandleNullAccuracyAndElevation() {
        // Given: Points with null accuracy and elevation
        Instant startTime = Instant.parse("2023-01-01T10:00:00Z");
        Instant endTime = startTime.plus(1, ChronoUnit.MINUTES);
        
        RawLocationPoint startPoint = new RawLocationPoint(
                1L, null, startTime, new GeoPoint(50.0, 8.0), null, null, false, false, 1L
        );
        RawLocationPoint endPoint = new RawLocationPoint(
                2L, null, endTime, new GeoPoint(50.001, 8.001), null, null, false, false, 1L
        );

        // When: Generate synthetic points
        List<LocationPoint> syntheticPoints = generator.generateSyntheticPoints(
            startPoint, endPoint, 4
        );

        // Then: Should generate points with null accuracy and elevation
        assertEquals(3, syntheticPoints.size());
        LocationPoint point = syntheticPoints.get(0);
        assertNull(point.getAccuracyMeters());
        assertNull(point.getElevationMeters());
    }

    @Test
    void shouldInterpolateRegardlessOfDistance() {
        // Given: Two points very far apart (~1.4km). The generator no longer applies a distance
        // cap: whether a gap is filled at all is decided upstream by implied speed, not here.
        Instant startTime = Instant.parse("2023-01-01T10:00:00Z");
        Instant endTime = startTime.plus(1, ChronoUnit.MINUTES);

        RawLocationPoint startPoint = new RawLocationPoint(
                1L, null, startTime, new GeoPoint(50.0, 8.0), 10.0, 100.0, false, false, 1L
        );
        RawLocationPoint endPoint = new RawLocationPoint(
                2L, null, endTime, new GeoPoint(50.01, 8.01), 20.0, 110.0, false, false, 1L
        );

        // When: Generate synthetic points
        List<LocationPoint> syntheticPoints = generator.generateSyntheticPoints(
            startPoint, endPoint, 4
        );

        // Then: Should generate 3 points, spanning the full distance
        assertEquals(3, syntheticPoints.size());
        assertTrue(syntheticPoints.get(0).getLatitude() > 50.0);
        assertTrue(syntheticPoints.get(2).getLatitude() < 50.01);
    }

    @Test
    void shouldFillStationaryClusterAcrossTheWholeGap() {
        // Given: two points 6 hours apart, the battery-saving shape
        Instant startTime = Instant.parse("2023-01-01T10:00:00Z");
        Instant endTime = startTime.plus(6, ChronoUnit.HOURS);

        RawLocationPoint startPoint = new RawLocationPoint(
                1L, null, startTime, new GeoPoint(50.0, 8.0), 10.0, 100.0, false, false, 1L
        );
        RawLocationPoint endPoint = new RawLocationPoint(
                2L, null, endTime, new GeoPoint(50.0027, 8.0017), 15.0, 105.0, false, false, 1L
        );

        // When: filling with a stationary cluster
        List<LocationPoint> syntheticPoints = generator.generateStationaryPoints(
                startPoint, endPoint, 4, 15.0
        );

        // Then: the fill runs to the last interval before the gap end, anchored at the first point
        assertEquals(1439, syntheticPoints.size());
        assertEquals(Instant.parse("2023-01-01T10:00:15Z"), syntheticPoints.get(0).getTimestamp());
        // the fill runs right up to the gap end, one sampling interval short of it
        assertEquals(endTime.minusSeconds(15), syntheticPoints.getLast().getTimestamp());
        assertTrue(syntheticPoints.getLast().getTimestamp().isBefore(endTime));

        // ...and every point stays clustered on the anchor. The jitter offsets latitude and
        // longitude independently, so the furthest a point can land is the radius along the
        // diagonal, not the radius itself.
        double maxDrift = 15.0 * Math.sqrt(2);
        for (LocationPoint point : syntheticPoints) {
            double distance = GeoUtils.distanceInMeters(
                    point.getLatitude(), point.getLongitude(), 50.0, 8.0);
            assertTrue(distance <= maxDrift, "stationary point drifted " + distance + "m from the anchor");
        }
    }

    @Test
    void shouldNotGeneratePointsForShortGaps() {
        // Given: Two points only 10 seconds apart
        Instant startTime = Instant.parse("2023-01-01T10:00:00Z");
        Instant endTime = startTime.plus(10, ChronoUnit.SECONDS);
        
        RawLocationPoint startPoint = new RawLocationPoint(
                1L, null, startTime, new GeoPoint(50.0, 8.0), 10.0, 100.0, false, false, 1L
        );
        RawLocationPoint endPoint = new RawLocationPoint(
                2L, null, endTime, new GeoPoint(50.0001, 8.0001), 15.0, 105.0, false, false, 1L
        );

        // When: Generate synthetic points for 4 points per minute (15 second intervals)
        List<LocationPoint> syntheticPoints = generator.generateSyntheticPoints(
            startPoint, endPoint, 4
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
                1L, null, startTime, new GeoPoint(50.0, 8.0), 10.0, 100.0, false, false, 1L
        );
        RawLocationPoint endPoint = new RawLocationPoint(
                2L, null, endTime, new GeoPoint(50.001, 8.001), 15.0, 105.0, false, false, 1L
        );

        // When: Generate synthetic points for 4 points per minute (15 second intervals)
        List<LocationPoint> syntheticPoints = generator.generateSyntheticPoints(
            startPoint, endPoint, 4
        );

        // Then: Should generate 4 points at 15, 30, 45, 60 seconds
        assertEquals(4, syntheticPoints.size());
        assertEquals(Instant.parse("2023-01-01T10:00:15Z"), syntheticPoints.get(0).getTimestamp());
        assertEquals(Instant.parse("2023-01-01T10:00:30Z"), syntheticPoints.get(1).getTimestamp());
        assertEquals(Instant.parse("2023-01-01T10:00:45Z"), syntheticPoints.get(2).getTimestamp());
        assertEquals(Instant.parse("2023-01-01T10:01:00Z"), syntheticPoints.get(3).getTimestamp());
    }
}
