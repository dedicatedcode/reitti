package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.event.LocationProcessEvent;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.NoVisitZone;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.SuppressedVisit;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.NoVisitZoneJdbcService;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.SuppressedVisitJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import com.dedicatedcode.reitti.service.SuppressedVisitService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@IntegrationTest
class VisitSuppressionTest {

    private static final Instant T0 = Instant.parse("2026-08-25T00:00:00Z");
    private static final double STAY1_LAT = 53.551086;
    private static final double STAY1_LON = 9.993682;
    private static final double STAY2_LAT = 54.051086;
    private static final double STAY2_LON = 10.493682;
    private static final double STAY3_LAT = 53.051086;
    private static final double STAY3_LON = 10.993682;
    private static final double STAY4_LAT = 53.551086;
    private static final double STAY4_LON = 11.493682;

    @Autowired
    private UnifiedLocationProcessingService processingService;
    @Autowired
    private SourceLocationPointJdbcService sourceLocationPointJdbcService;
    @Autowired
    private RawLocationPointJdbcService rawLocationPointJdbcService;
    @Autowired
    private ProcessedVisitJdbcService processedVisitJdbcService;
    @Autowired
    private TripJdbcService tripJdbcService;
    @Autowired
    private SuppressedVisitJdbcService suppressedVisitJdbcService;
    @Autowired
    private NoVisitZoneJdbcService noVisitZoneJdbcService;
    @Autowired
    private SuppressedVisitService suppressedVisitService;
    @Autowired
    private TestingService testingService;

    private User user;

    @BeforeEach
    void setUp() {
        this.user = testingService.randomUser();
    }

    @Test
    void shouldNotCreateVisitsInsideNoVisitZone() {
        seedTwoStaysWithMovement();
        process();

        assertEquals(2, sortedVisits().size());

        NoVisitZone zone = noVisitZoneJdbcService.create(user, new NoVisitZone("Test Zone", squareAround(STAY2_LAT, STAY2_LON, 0.002)));
        process();

        List<ProcessedVisit> remaining = sortedVisits();
        assertEquals(1, remaining.size());
        assertEquals(STAY1_LAT, remaining.getFirst().getPlace().getLatitudeCentroid(), 0.01);

        noVisitZoneJdbcService.delete(user, zone.id());
        process();

        assertEquals(2, sortedVisits().size());
    }

    @Test
    void shouldNotRecreateSuppressedVisit() {
        seedTwoStaysWithMovement();
        process();

        List<ProcessedVisit> initial = sortedVisits();
        assertEquals(2, initial.size());
        assertEquals(1, tripJdbcService.findByUser(user).size());

        ProcessedVisit first = initial.getFirst();
        SuppressedVisit suppressed = suppressedVisitJdbcService.create(user, new SuppressedVisit(
                first.getPlace().getId(),
                first.getPlace().getLatitudeCentroid(),
                first.getPlace().getLongitudeCentroid(),
                first.getStartTime(),
                first.getEndTime()));
        process();

        List<ProcessedVisit> remaining = sortedVisits();
        assertEquals(1, remaining.size());
        assertEquals(initial.getLast().getStartTime(), remaining.getFirst().getStartTime());
        assertEquals(0, tripJdbcService.findByUser(user).size());

        suppressedVisitJdbcService.delete(user, suppressed.id());
        process();

        assertEquals(2, sortedVisits().size());
        assertEquals(1, tripJdbcService.findByUser(user).size());
    }

    @Test
    void shouldSuppressByCentroidWhenPlaceIdIsUnknown() {
        seedTwoStaysWithMovement();
        process();

        ProcessedVisit first = sortedVisits().getFirst();
        suppressedVisitJdbcService.create(user, new SuppressedVisit(
                null,
                first.getPlace().getLatitudeCentroid(),
                first.getPlace().getLongitudeCentroid(),
                first.getStartTime(),
                first.getEndTime()));
        process();

        assertEquals(1, sortedVisits().size());
    }

    @Test
    void shouldNotSuppressVisitAtDifferentPlace() {
        seedTwoStaysWithMovement();
        process();

        ProcessedVisit first = sortedVisits().getFirst();
        suppressedVisitJdbcService.create(user, new SuppressedVisit(
                null,
                40.0,
                20.0,
                first.getStartTime(),
                first.getEndTime()));
        process();

        assertEquals(2, sortedVisits().size());
    }

    @Test
    void shouldSuppressVisitAndRemoveItThroughRecalculation() {
        seedTwoStaysWithMovement();
        process();

        ProcessedVisit first = sortedVisits().getFirst();
        suppressedVisitService.suppressVisit(user, first);

        Awaitility.await("waiting for recalculation to remove the suppressed visit")
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> sortedVisits().size() == 1);

        List<SuppressedVisit> tombstones = suppressedVisitJdbcService.findByUser(user);
        assertEquals(1, tombstones.size());

        SuppressedVisit tombstone = tombstones.getFirst();
        suppressedVisitJdbcService.delete(user, tombstone.id());
        rawLocationPointJdbcService.markUnprocessedForUserAndTimeRange(user, tombstone.startTime(), tombstone.endTime());
        process();

        assertEquals(2, sortedVisits().size());
        assertEquals(1, tripJdbcService.findByUser(user).size());
    }

    @Test
    void shouldReconnectSurroundingVisitsWhenAllVisitsInWindowAreSuppressed() {
        List<LocationPoint> points = new ArrayList<>();
        seedStayAt(points, STAY1_LAT, STAY1_LON, T0, 2);
        seedMovement(points, STAY1_LAT, STAY1_LON, STAY2_LAT, STAY2_LON, T0.plus(2, ChronoUnit.HOURS), 1);
        seedStayAt(points, STAY2_LAT, STAY2_LON, T0.plus(3, ChronoUnit.HOURS), 2);
        seedMovement(points, STAY2_LAT, STAY2_LON, STAY3_LAT, STAY3_LON, T0.plus(5, ChronoUnit.HOURS), 1);
        seedStayAt(points, STAY3_LAT, STAY3_LON, T0.plus(6, ChronoUnit.HOURS), 2);
        seedMovement(points, STAY3_LAT, STAY3_LON, STAY4_LAT, STAY4_LON, T0.plus(8, ChronoUnit.HOURS), 1);
        seedStayAt(points, STAY4_LAT, STAY4_LON, T0.plus(9, ChronoUnit.HOURS), 2);
        seed(points, TimeRange.of(T0, T0.plus(11, ChronoUnit.HOURS)));
        process(T0, T0.plus(11, ChronoUnit.HOURS));

        assertEquals(4, sortedVisits().size());
        assertEquals(3, tripJdbcService.findByUser(user).size());

        List<ProcessedVisit> visits = sortedVisits();
        ProcessedVisit second = visits.get(1);
        ProcessedVisit third = visits.get(2);
        suppressedVisitJdbcService.create(user, new SuppressedVisit(second.getPlace().getId(), second.getPlace().getLatitudeCentroid(), second.getPlace().getLongitudeCentroid(), second.getStartTime(), second.getEndTime()));
        suppressedVisitJdbcService.create(user, new SuppressedVisit(third.getPlace().getId(), third.getPlace().getLatitudeCentroid(), third.getPlace().getLongitudeCentroid(), third.getStartTime(), third.getEndTime()));

        process(second.getStartTime(), third.getEndTime());

        List<ProcessedVisit> remaining = sortedVisits();
        assertEquals(2, remaining.size());
        List<Trip> trips = tripJdbcService.findByUser(user).stream().sorted(Comparator.comparing(Trip::getStartTime)).toList();
        assertEquals(1, trips.size());
        assertEquals(remaining.get(0).getEndTime(), trips.getFirst().getStartTime());
        assertEquals(remaining.get(1).getStartTime(), trips.getFirst().getEndTime());

        for (SuppressedVisit tombstone : suppressedVisitJdbcService.findByUser(user)) {
            suppressedVisitJdbcService.delete(user, tombstone.id());
        }
        rawLocationPointJdbcService.markUnprocessedForUserAndTimeRange(user, second.getStartTime(), third.getEndTime());
        process(T0, T0.plus(11, ChronoUnit.HOURS));

        assertEquals(4, sortedVisits().size());
        assertEquals(3, tripJdbcService.findByUser(user).size());
    }

    private void seedTwoStaysWithMovement() {
        List<LocationPoint> points = new ArrayList<>();
        seedStayAt(points, STAY1_LAT, STAY1_LON, T0, 2);
        seedMovement(points, STAY1_LAT, STAY1_LON, STAY2_LAT, STAY2_LON, T0.plus(2, ChronoUnit.HOURS), 1);
        seedStayAt(points, STAY2_LAT, STAY2_LON, T0.plus(3, ChronoUnit.HOURS), 2);
        seed(points, TimeRange.of(T0, T0.plus(5, ChronoUnit.HOURS)));
    }

    private void seed(List<LocationPoint> points, TimeRange range) {
        sourceLocationPointJdbcService.bulkInsert(user, testingService.findDefaultDevice(user), points);
        rawLocationPointJdbcService.dropForReSeeding(user, range);
        rawLocationPointJdbcService.updateFromDevices(user, range);
    }

    private void seedStayAt(List<LocationPoint> points, double latitude, double longitude, Instant start, int hours) {
        for (int i = 0; i < hours * 240; i++) {
            points.add(point(latitude, longitude, start.plus(i * 15L, ChronoUnit.SECONDS)));
        }
    }

    private void seedMovement(List<LocationPoint> points, double fromLat, double fromLon, double toLat, double toLon, Instant start, int hours) {
        int steps = hours * 240;
        for (int i = 0; i < steps; i++) {
            double fraction = (double) i / steps;
            points.add(point(fromLat + (toLat - fromLat) * fraction,
                    fromLon + (toLon - fromLon) * fraction,
                    start.plus(i * 15L, ChronoUnit.SECONDS)));
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

    private void process() {
        process(T0, T0.plus(5, ChronoUnit.HOURS));
    }

    private void process(Instant start, Instant end) {
        processingService.processLocationEvent(new LocationProcessEvent(user.getUsername(), start, end, null, null, null));
    }

    private List<ProcessedVisit> sortedVisits() {
        return processedVisitJdbcService.findByUser(user).stream()
                .sorted(Comparator.comparing(ProcessedVisit::getStartTime))
                .toList();
    }

    private List<GeoPoint> squareAround(double latitude, double longitude, double delta) {
        return List.of(
                new GeoPoint(latitude - delta, longitude - delta),
                new GeoPoint(latitude - delta, longitude + delta),
                new GeoPoint(latitude + delta, longitude + delta),
                new GeoPoint(latitude + delta, longitude - delta));
    }
}
