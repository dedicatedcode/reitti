package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.event.LocationProcessEvent;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@IntegrationTest
class SamePlaceMergeGapTest {

    private static final Instant T0 = Instant.parse("2026-08-25T00:00:00Z");
    private static final double LAT = 53.551086;
    private static final double LON = 9.993682;

    @Autowired
    private UnifiedLocationProcessingService processingService;
    @Autowired
    private SourceLocationPointJdbcService sourceLocationPointJdbcService;
    @Autowired
    private RawLocationPointJdbcService rawLocationPointJdbcService;
    @Autowired
    private TestingService testingService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User user;

    @BeforeEach
    void setUp() {
        this.user = testingService.randomUser();
    }

    @Test
    void mergesStaysAtTheSamePlaceAcrossAShortGapWithoutData() {
        Instant secondStayStart = seedTwoStaysAtTheSamePlace(Duration.ofHours(10));

        List<Map<String, Object>> visits = visits();
        assertEquals(1, visits.size());
        assertEquals(T0, ((Timestamp) visits.getFirst().get("start_time")).toInstant());
        assertEquals(lastPointOfStayStartingAt(secondStayStart), ((Timestamp) visits.getFirst().get("end_time")).toInstant());
    }

    @Test
    void doesNotMergeStaysAtTheSamePlaceAcrossDaysWithoutData() {
        Instant secondStayStart = seedTwoStaysAtTheSamePlace(Duration.ofDays(3));

        List<Map<String, Object>> visits = visits();
        assertEquals(2, visits.size(), "a data gap of days must not become one visit spanning it");
        assertEquals(lastPointOfStayStartingAt(T0), ((Timestamp) visits.get(0).get("end_time")).toInstant());
        assertEquals(secondStayStart, ((Timestamp) visits.get(1).get("start_time")).toInstant());
        assertEquals(1L, jdbcTemplate.queryForObject("SELECT count(DISTINCT place_id) FROM processed_visits WHERE user_id = ?", Long.class, user.getId()));
        assertEquals(0L, jdbcTemplate.queryForObject("SELECT count(*) FROM trips WHERE user_id = ?", Long.class, user.getId()));
    }

    /**
     * Two 2h stays at the same coordinates, the given gap without any point between them, processed in one run.
     *
     * @return the start of the second stay
     */
    private Instant seedTwoStaysAtTheSamePlace(Duration gap) {
        Instant secondStayStart = lastPointOfStayStartingAt(T0).plus(gap);
        List<LocationPoint> points = new ArrayList<>();
        addStay(points, T0);
        addStay(points, secondStayStart);

        TimeRange range = TimeRange.of(T0, secondStayStart.plus(2, ChronoUnit.HOURS));
        sourceLocationPointJdbcService.bulkInsert(user, testingService.findDefaultDevice(user), points);
        rawLocationPointJdbcService.dropForReSeeding(user, range);
        rawLocationPointJdbcService.updateFromDevices(user, range);
        processingService.processLocationEvent(new LocationProcessEvent(user.getUsername(), range.start(), range.end(), null, null, null));
        return secondStayStart;
    }

    private void addStay(List<LocationPoint> points, Instant start) {
        for (int i = 0; i < 2 * 240; i++) {
            LocationPoint point = new LocationPoint();
            point.setLatitude(LAT);
            point.setLongitude(LON);
            point.setTimestamp(start.plus(i * 15L, ChronoUnit.SECONDS));
            point.setAccuracyMeters(10.0);
            points.add(point);
        }
    }

    private Instant lastPointOfStayStartingAt(Instant start) {
        return start.plus((2 * 240 - 1) * 15L, ChronoUnit.SECONDS);
    }

    private List<Map<String, Object>> visits() {
        return jdbcTemplate.queryForList("SELECT start_time, end_time FROM processed_visits WHERE user_id = ? ORDER BY start_time", user.getId());
    }
}
