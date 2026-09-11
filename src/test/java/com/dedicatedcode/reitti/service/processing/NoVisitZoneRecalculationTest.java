package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.event.LocationProcessEvent;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.NoVisitZone;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.NoVisitZoneJdbcService;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
import com.dedicatedcode.reitti.service.NoVisitZoneService;
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
class NoVisitZoneRecalculationTest {

    private static final Instant T0 = Instant.parse("2026-08-25T00:00:00Z");
    private static final double STAY1_LAT = 53.551086;
    private static final double STAY1_LON = 9.993682;
    private static final double STAY2_LAT = 54.051086;
    private static final double STAY2_LON = 10.493682;

    @Autowired
    private UnifiedLocationProcessingService processingService;
    @Autowired
    private SourceLocationPointJdbcService sourceLocationPointJdbcService;
    @Autowired
    private RawLocationPointJdbcService rawLocationPointJdbcService;
    @Autowired
    private ProcessedVisitJdbcService processedVisitJdbcService;
    @Autowired
    private NoVisitZoneJdbcService noVisitZoneJdbcService;
    @Autowired
    private NoVisitZoneService noVisitZoneService;
    @Autowired
    private TestingService testingService;

    private User user;

    @BeforeEach
    void setUp() {
        this.user = testingService.randomUser();
    }

    @Test
    void shouldRemoveVisitsInsideZoneOnCreateAndRestoreThemOnDelete() {
        seedTwoStaysWithMovement();
        process();

        assertEquals(2, sortedVisits().size());

        NoVisitZone zone = noVisitZoneService.create(user, new NoVisitZone("Test Zone", squareAround(STAY2_LAT, STAY2_LON, 0.002)));

        Awaitility.await("waiting for recalculation to remove visits inside the zone")
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> sortedVisits().size() == 1 && noUnprocessedPointsLeft());

        assertEquals(STAY1_LAT, sortedVisits().getFirst().getPlace().getLatitudeCentroid(), 0.01);

        noVisitZoneService.delete(user, zone);

        Awaitility.await("waiting for recalculation to restore visits after zone deletion")
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> sortedVisits().size() == 2);
    }

    private boolean noUnprocessedPointsLeft() {
        return rawLocationPointJdbcService.findByUserAndProcessedIsFalseOrderByTimestampWithLimit(user, 1, 0).isEmpty();
    }

    private void seedTwoStaysWithMovement() {
        List<LocationPoint> points = new ArrayList<>();
        for (int i = 0; i < 480; i++) {
            points.add(point(STAY1_LAT, STAY1_LON, T0.plus(i * 15L, ChronoUnit.SECONDS)));
        }
        for (int i = 0; i < 240; i++) {
            double fraction = i / 240.0;
            points.add(point(STAY1_LAT + (STAY2_LAT - STAY1_LAT) * fraction,
                    STAY1_LON + (STAY2_LON - STAY1_LON) * fraction,
                    T0.plus(2 * 3600 + i * 15L, ChronoUnit.SECONDS)));
        }
        for (int i = 0; i < 480; i++) {
            points.add(point(STAY2_LAT, STAY2_LON, T0.plus(3 * 3600 + i * 15L, ChronoUnit.SECONDS)));
        }
        sourceLocationPointJdbcService.bulkInsert(user, testingService.findDefaultDevice(user), points);
        TimeRange range = TimeRange.of(T0, T0.plus(5, ChronoUnit.HOURS));
        rawLocationPointJdbcService.dropForReSeeding(user, range);
        rawLocationPointJdbcService.updateFromDevices(user, range);
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
        processingService.processLocationEvent(new LocationProcessEvent(user.getUsername(), T0, T0.plus(5, ChronoUnit.HOURS), null, null, null));
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
