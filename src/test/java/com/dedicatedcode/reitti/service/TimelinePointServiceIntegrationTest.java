package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
@TestPropertySource(properties = "reitti.processing.point-chunk-size=2")
class TimelinePointServiceIntegrationTest {

    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    private TimelinePointService timelinePointService;
    @Autowired
    private RawLocationPointJdbcService rawLocationPointJdbcService;
    @Autowired
    private TestingService testingService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User user;

    @BeforeEach
    void setUp() {
        testingService.clearData();
        user = testingService.randomUser();
    }

    @Test
    void streamIncludesGeneratedSyntheticPoints() {
        createPoint(START, 50.0, 8.0);
        createPoint(START.plus(2, ChronoUnit.MINUTES), 50.0001, 8.0001);

        RawLocationPointStream stream = timelinePointService.stream(
                user, START, START.plus(3, ChronoUnit.MINUTES), true, false);

        List<RawLocationPoint> points = toList(stream);
        assertTrue(points.size() > 2, "expected generated points between the real points");
        assertTrue(points.stream().anyMatch(p -> p.getId() < 0));
        assertOrdered(points);
    }

    @Test
    void streamWithoutSyntheticReturnsOnlyRealPoints() {
        createPoint(START, 50.0, 8.0);
        createPoint(START.plus(2, ChronoUnit.MINUTES), 50.0001, 8.0001);

        List<RawLocationPoint> points = toList(timelinePointService.stream(
                user, START, START.plus(3, ChronoUnit.MINUTES), false, false));

        assertEquals(2, points.size());
        assertTrue(points.stream().noneMatch(RawLocationPoint::isGenerated));
    }

    @Test
    void ignoredRealPointIsFilteredButStillAnchorsBothGaps() {
        RawLocationPoint first = createPoint(START, 50.0, 8.0);
        RawLocationPoint ignored = createPoint(START.plus(2, ChronoUnit.MINUTES), 50.0001, 8.0001);
        createPoint(START.plus(4, ChronoUnit.MINUTES), 50.0002, 8.0002);
        jdbcTemplate.update("UPDATE raw_location_points SET status = 2 WHERE id = ?", ignored.getId());

        List<RawLocationPoint> filtered = toList(timelinePointService.stream(
                user, START, START.plus(5, ChronoUnit.MINUTES), true, false));
        assertFalse(filtered.stream().anyMatch(p -> p.getTimestamp().equals(ignored.getTimestamp()) && !p.isGenerated()),
                "ignored real point must not be emitted");
        assertTrue(filtered.stream().filter(RawLocationPoint::isGenerated)
                .anyMatch(p -> p.getTimestamp().isAfter(first.getTimestamp())
                        && p.getTimestamp().isBefore(ignored.getTimestamp())),
                "gap before the ignored point must be filled");
        assertTrue(filtered.stream().filter(RawLocationPoint::isGenerated)
                .anyMatch(p -> p.getTimestamp().isAfter(ignored.getTimestamp())),
                "gap after the ignored point must be filled");

        List<RawLocationPoint> included = toList(timelinePointService.stream(
                user, START, START.plus(5, ChronoUnit.MINUTES), true, true));
        assertTrue(included.stream().anyMatch(p -> p.getTimestamp().equals(ignored.getTimestamp()) && !p.isGenerated()),
                "ignored real point must be emitted when requested");
    }

    @Test
    void edgeAnchorsFillGapsAtRangeBoundaries() {
        createPoint(START.minus(10, ChronoUnit.MINUTES), 50.0, 8.0);
        createPoint(START.plus(1, ChronoUnit.MINUTES), 50.0001, 8.0001);
        createPoint(START.plus(9, ChronoUnit.MINUTES), 50.0002, 8.0002);
        createPoint(START.plus(20, ChronoUnit.MINUTES), 50.0003, 8.0003);

        List<RawLocationPoint> points = toList(timelinePointService.stream(
                user, START, START.plus(10, ChronoUnit.MINUTES), true, false));

        assertTrue(points.stream().filter(RawLocationPoint::isGenerated)
                .anyMatch(p -> p.getTimestamp().isBefore(START.plus(1, ChronoUnit.MINUTES))),
                "left edge gap should be filled");
        assertTrue(points.stream().filter(RawLocationPoint::isGenerated)
                .anyMatch(p -> p.getTimestamp().isAfter(START.plus(9, ChronoUnit.MINUTES))),
                "right edge gap should be filled");
        assertTrue(points.stream().allMatch(p -> !p.getTimestamp().isBefore(START)
                && p.getTimestamp().isBefore(START.plus(10, ChronoUnit.MINUTES))));
    }

    @Test
    void manualWallSuppressesGenerationAcrossChunks() {
        createPoint(START, 50.0, 8.0);
        createPoint(START.plus(2, ChronoUnit.MINUTES), 50.0001, 8.0001);
        insertWall(START.plus(1, ChronoUnit.MINUTES));

        List<RawLocationPoint> points = toList(timelinePointService.stream(
                user, START, START.plus(3, ChronoUnit.MINUTES), true, false));

        assertTrue(points.stream().noneMatch(RawLocationPoint::isGenerated),
                "a gap containing a manual wall must not be filled");
    }

    @Test
    void chunkBoundariesDoNotSkipOrDuplicateGeneratedPoints() {
        List<RawLocationPoint> real = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            real.add(createPoint(START.plus(i * 10L, ChronoUnit.MINUTES), 50.0 + i * 0.00001, 8.0));
        }

        List<RawLocationPoint> points = toList(timelinePointService.stream(
                user, START, START.plus(70, ChronoUnit.MINUTES), true, false));

        assertOrdered(points);
        for (RawLocationPoint point : real) {
            assertTrue(points.stream().anyMatch(p -> p.getId().equals(point.getId())
                    && !p.isGenerated()), "real point " + point.getTimestamp() + " must be present");
        }
        assertTrue(points.stream().anyMatch(RawLocationPoint::isGenerated));
    }

    private RawLocationPoint createPoint(Instant timestamp, double lat, double lon) {
        return rawLocationPointJdbcService.create(user, new RawLocationPoint(
                null, null, timestamp, new GeoPoint(lat, lon), 10.0, 100.0, true, 1L));
    }

    private void insertWall(Instant timestamp) {
        jdbcTemplate.update("INSERT INTO raw_source_points (user_id, timestamp, accuracy_meters, geom, status) " +
                        "VALUES (?, ?, 10.0, ST_GeomFromText('POINT(8 50)', 4326), 1)",
                user.getId(), Timestamp.from(timestamp));
    }

    private static List<RawLocationPoint> toList(RawLocationPointStream stream) {
        List<RawLocationPoint> points = new ArrayList<>();
        for (RawLocationPoint point : stream) {
            points.add(point);
        }
        return points;
    }

    private static void assertOrdered(List<RawLocationPoint> points) {
        for (int i = 1; i < points.size(); i++) {
            assertTrue(points.get(i - 1).getTimestamp().isBefore(points.get(i).getTimestamp()),
                    "stream must be strictly ordered at index " + i);
        }
    }
}
