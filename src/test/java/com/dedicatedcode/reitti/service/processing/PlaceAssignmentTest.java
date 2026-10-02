package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.event.LocationProcessEvent;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.SignificantPlaceJdbcService;
import com.dedicatedcode.reitti.repository.SignificantPlaceOverrideJdbcService;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@IntegrationTest
class PlaceAssignmentTest {

    private static final Instant T0 = Instant.parse("2026-08-25T08:00:00Z");

    @Autowired
    private UnifiedLocationProcessingService processingService;
    @Autowired
    private SourceLocationPointJdbcService sourceLocationPointJdbcService;
    @Autowired
    private RawLocationPointJdbcService rawLocationPointJdbcService;
    @Autowired
    private ProcessedVisitJdbcService processedVisitJdbcService;
    @Autowired
    private SignificantPlaceJdbcService significantPlaceJdbcService;
    @Autowired
    private SignificantPlaceOverrideJdbcService significantPlaceOverrideJdbcService;
    @Autowired
    private TestingService testingService;

    private User user;

    @BeforeEach
    void setUp() {
        this.user = testingService.randomUser();
    }

    @Test
    void visitInsideAPlacePolygonBelongsToThatPlaceEvenIfAnotherCentroidIsCloser() {
        // a store with a drawn outline and a small shop attached to its north side
        SignificantPlace store = testingService.newSignificantPlace(user, 53.5500, 10.0000, "Store");
        significantPlaceJdbcService.update(store.withPolygon(List.of(
                new GeoPoint(53.5490, 9.9985),
                new GeoPoint(53.5490, 10.0015),
                new GeoPoint(53.5510, 10.0015),
                new GeoPoint(53.5510, 9.9985))));
        testingService.newSignificantPlace(user, 53.5512, 10.0000, "Shop");

        // inside the store, ~45m from the shop's centroid and ~90m from the store's
        stayAndProcess(53.5508, 10.0000);

        List<ProcessedVisit> visits = processedVisitJdbcService.findByUser(user);
        assertEquals(1, visits.size());
        assertEquals(store.getId(), visits.getFirst().getPlace().getId());
    }

    @Test
    void recreatedPlaceKeepsTheNameTheUserGaveItsPredecessor() {
        // the user renamed a place, which was later removed and is now recreated from a visit ~15m off its centroid
        significantPlaceOverrideJdbcService.insertOverride(user, SignificantPlace.create(53.5500, 10.0000)
                .withName("Renamed Place")
                .withTimezone(ZoneId.of("Europe/Berlin")));

        stayAndProcess(53.55014, 10.0000);

        List<ProcessedVisit> visits = processedVisitJdbcService.findByUser(user);
        assertEquals(1, visits.size());
        assertEquals("Renamed Place", visits.getFirst().getPlace().getName());
    }

    private void stayAndProcess(double latitude, double longitude) {
        List<LocationPoint> points = new ArrayList<>();
        for (int i = 0; i < 2 * 240; i++) {
            LocationPoint point = new LocationPoint();
            point.setLatitude(latitude);
            point.setLongitude(longitude);
            point.setTimestamp(T0.plus(i * 15L, ChronoUnit.SECONDS));
            point.setAccuracyMeters(10.0);
            points.add(point);
        }
        TimeRange range = TimeRange.of(T0, T0.plus(2, ChronoUnit.HOURS));
        sourceLocationPointJdbcService.bulkInsert(user, testingService.findDefaultDevice(user), points);
        rawLocationPointJdbcService.dropForReSeeding(user, range);
        rawLocationPointJdbcService.updateFromDevices(user, range);
        processingService.processLocationEvent(new LocationProcessEvent(user.getUsername(), range.start(), range.end(), null, null, null));
    }
}
