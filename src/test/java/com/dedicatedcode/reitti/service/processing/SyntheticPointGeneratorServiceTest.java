package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.GeoUtils;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.processing.DetectionParameter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class SyntheticPointGeneratorServiceTest {

    private static final DetectionParameter.LocationDensity DENSITY =
            new DetectionParameter.LocationDensity(150, 1440);

    @Autowired
    private SyntheticPointGeneratorService generator;

    @Test
    void shouldGenerateSyntheticPointsForLargeGaps() {
        Instant start = Instant.parse("2023-01-01T10:00:00Z");
        Instant end = start.plus(2, ChronoUnit.MINUTES);
        List<RawLocationPoint> synthetic = generator.generateGap(
                point(start, 50.0, 8.0), point(end, 50.0001, 8.0001), DENSITY, walls());
        assertFalse(synthetic.isEmpty(), "Should have generated synthetic points");
    }

    @Test
    void shouldInterpolateShortFastGaps() {
        Instant start = Instant.parse("2023-01-01T10:00:00Z");
        Instant end = start.plus(2, ChronoUnit.MINUTES);
        List<RawLocationPoint> synthetic = generator.generateGap(
                point(start, 50.0, 8.0), point(end, 50.01, 8.01), DENSITY, walls());
        assertFalse(synthetic.isEmpty(), "Short gaps above the stationary speed bound should be interpolated");
    }

    @Test
    void shouldFillLongDistanceGapsWithStationaryCluster() {
        Instant start = Instant.parse("2023-01-01T10:00:00Z");
        Instant end = start.plus(3, ChronoUnit.HOURS);
        List<RawLocationPoint> synthetic = generator.generateGap(
                point(start, 50.0, 8.0), point(end, 50.005, 8.005), DENSITY, walls());
        assertFalse(synthetic.isEmpty(), "Long gaps below the stationary speed bound should be filled");
        for (RawLocationPoint p : synthetic) {
            double distance = GeoUtils.distanceInMeters(p.getGeom(), new GeoPoint(50.0, 8.0));
            assertTrue(distance <= 50.0, "Stationary synthetic point must stay near the anchor, was " + distance + "m");
        }
    }

    @Test
    void shouldFillBatterySavingGapAsSingleContiguousRun() {
        Instant start = Instant.parse("2023-01-01T10:00:00Z");
        Instant end = start.plus(6, ChronoUnit.HOURS);
        List<RawLocationPoint> synthetic = generator.generateGap(
                point(start, 50.0, 8.0), point(end, 50.0027, 8.0017), DENSITY, walls());
        assertFalse(synthetic.isEmpty(), "a 6h / 300m gap should be filled as stationary");
        assertEquals(end.minusSeconds(15), synthetic.getLast().getTimestamp(),
                "stationary fill should reach the last real point");
        for (int i = 1; i < synthetic.size(); i++) {
            long between = Duration.between(synthetic.get(i - 1).getTimestamp(), synthetic.get(i).getTimestamp()).getSeconds();
            assertTrue(between <= 16, "hole of " + between + "s inside the stationary fill");
        }
    }

    @Test
    void shouldNotFillGapsBeyondTheInterpolationCeiling() {
        Instant start = Instant.parse("2023-01-01T10:00:00Z");
        Instant end = start.plus(14, ChronoUnit.HOURS);
        List<RawLocationPoint> synthetic = generator.generateGap(
                point(start, 50.0, 8.0), point(end, 50.36, 8.36), DENSITY, walls());
        assertTrue(synthetic.isEmpty(), "Gaps beyond the interpolation ceiling stay unfilled");
    }

    @Test
    void shouldNotFillGapsContainingAManuallyIgnoredWall() {
        Instant start = Instant.parse("2023-01-01T10:00:00Z");
        Instant end = start.plus(2, ChronoUnit.HOURS);
        NavigableSet<Instant> walls = walls(start.plus(30, ChronoUnit.MINUTES));
        List<RawLocationPoint> synthetic = generator.generateGap(
                point(start, 50.0, 8.0), point(end, 50.001, 8.001), DENSITY, walls);
        assertTrue(synthetic.isEmpty(), "A gap containing a wall must not be filled");
    }

    @Test
    void shouldGenerateExpectedNumberOfSyntheticPointsForGivenRealPoints() {
        Instant[] timestamps = {
                Instant.parse("2013-04-15T06:31:26.860000Z"),
                Instant.parse("2013-04-15T06:32:31.475000Z"),
                Instant.parse("2013-04-15T06:33:32.406000Z"),
                Instant.parse("2013-04-15T06:34:32.478000Z"),
                Instant.parse("2013-04-15T06:35:32.492000Z"),
                Instant.parse("2013-04-15T06:36:32.566000Z")
        };
        int total = 0;
        for (int i = 1; i < timestamps.length; i++) {
            total += generator.generateGap(point(timestamps[i - 1], 50.0, 8.0), point(timestamps[i], 50.0, 8.0), DENSITY, walls()).size();
        }
        assertEquals(20, total, "Exactly 20 synthetic points");
    }

    @Test
    void shouldHandleSinglePointGracefully() {
        Instant start = Instant.parse("2023-01-01T10:00:00Z");
        assertTrue(generator.generateGap(point(start, 50.0, 8.0), point(start, 50.0, 8.0), DENSITY, walls()).isEmpty());
    }

    private static RawLocationPoint point(Instant timestamp, double lat, double lon) {
        return new RawLocationPoint(null, null, timestamp, new GeoPoint(lat, lon), 10.0, 100.0, true, 1L);
    }

    private static NavigableSet<Instant> walls(Instant... walls) {
        return new TreeSet<>(List.of(walls));
    }
}
