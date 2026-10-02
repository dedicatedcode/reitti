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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A run that touches one end of a trip deletes the visit at that end together with the trip (ON DELETE CASCADE)
 * and has to rebuild the trip. The rebuilt trips must be the same as if all data had been processed in one run.
 */
@IntegrationTest
class TripRebuildConsistencyTest {

    private static final Instant T0 = Instant.parse("2026-08-25T00:00:00Z");
    private static final double START_LAT = 53.551086;
    private static final double START_LON = 9.993682;
    private static final double END_LAT = 58.551086;
    private static final double END_LON = 14.993682;

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
    void tripIsRebuiltWhenALaterRunReplacesTheVisitAtItsEnd() {
        Instant secondStayStart = seedStayTravelStay(6);
        processingService.processLocationEvent(event(T0, secondStayStart.plus(2, ChronoUnit.HOURS)));
        List<String> tripsOfSingleRun = trips();
        assertEquals(1, tripsOfSingleRun.size());

        reprocessInsideSecondStay(secondStayStart);

        assertEquals(tripsOfSingleRun, trips());
    }

    @Test
    void tripsLongerThanTheLimitAreNeitherCreatedInOneRunNorAcrossRuns() {
        Instant secondStayStart = seedStayTravelStay(26);
        processingService.processLocationEvent(event(T0, secondStayStart.plus(2, ChronoUnit.HOURS)));
        assertEquals(2, visitCount());
        List<String> tripsOfSingleRun = trips();

        reprocessInsideSecondStay(secondStayStart);

        assertEquals(2, visitCount());
        assertEquals(tripsOfSingleRun, trips(), "a later run must not change the trips between the visits");
        assertEquals(List.of(), tripsOfSingleRun);
    }

    private void reprocessInsideSecondStay(Instant secondStayStart) {
        // new points in the middle of the second stay: the run replaces that visit and its incoming trip
        Instant touched = secondStayStart.plus(1, ChronoUnit.HOURS);
        processingService.processLocationEvent(event(touched, touched.plus(1, ChronoUnit.MINUTES)));
    }

    /**
     * Two 2h stays, connected by continuous travel of the given duration.
     *
     * @return the start of the second stay
     */
    private Instant seedStayTravelStay(int travelHours) {
        List<LocationPoint> points = new ArrayList<>();
        Instant travelStart = T0.plus(2, ChronoUnit.HOURS);
        Instant secondStayStart = travelStart.plus(travelHours, ChronoUnit.HOURS);
        addStay(points, START_LAT, START_LON, T0);
        int steps = travelHours * 240;
        for (int i = 0; i < steps; i++) {
            double fraction = (double) i / steps;
            points.add(point(START_LAT + (END_LAT - START_LAT) * fraction,
                    START_LON + (END_LON - START_LON) * fraction,
                    travelStart.plus(i * 15L, ChronoUnit.SECONDS)));
        }
        addStay(points, END_LAT, END_LON, secondStayStart);

        TimeRange range = TimeRange.of(T0, secondStayStart.plus(2, ChronoUnit.HOURS));
        sourceLocationPointJdbcService.bulkInsert(user, testingService.findDefaultDevice(user), points);
        rawLocationPointJdbcService.dropForReSeeding(user, range);
        rawLocationPointJdbcService.updateFromDevices(user, range);
        return secondStayStart;
    }

    private void addStay(List<LocationPoint> points, double latitude, double longitude, Instant start) {
        for (int i = 0; i < 2 * 240; i++) {
            points.add(point(latitude, longitude, start.plus(i * 15L, ChronoUnit.SECONDS)));
        }
    }

    private LocationPoint point(double latitude, double longitude, Instant timestamp) {
        LocationPoint point = new LocationPoint();
        point.setLatitude(latitude);
        point.setLongitude(longitude);
        point.setTimestamp(timestamp);
        point.setAccuracyMeters(10.0);
        return point;
    }

    private LocationProcessEvent event(Instant start, Instant end) {
        return new LocationProcessEvent(user.getUsername(), start, end, null, null, null);
    }

    private long visitCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM processed_visits WHERE user_id = ?", Long.class, user.getId());
    }

    private List<String> trips() {
        return jdbcTemplate.queryForList("SELECT start_time || ' -> ' || end_time FROM trips WHERE user_id = ? ORDER BY start_time",
                String.class, user.getId());
    }
}
