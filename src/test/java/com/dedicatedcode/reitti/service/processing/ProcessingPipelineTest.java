package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.UserType;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.geo.SourceLocationPoint;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.LocationBatchingService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.dedicatedcode.reitti.TestConstants.Points.*;
import static com.dedicatedcode.reitti.TestUtils.assertTrip;
import static com.dedicatedcode.reitti.TestUtils.assertVisit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
public class ProcessingPipelineTest {

    @Autowired
    private TestingService testingService;
    @Autowired
    private ProcessedVisitJdbcService processedVisitJdbcService;
    @Autowired
    private TripJdbcService tripJdbcService;
    @Autowired
    private RawLocationPointJdbcService rawLocationPointJdbcService;
    @Autowired
    private UserJdbcService userJdbcService;
    @Autowired
    private LocationBatchingService locationBatchingService;
    @Autowired
    private SourceLocationPointJdbcService sourceLocationPointJdbcService;

    private User user;

    @BeforeEach
    public void setUp() {
        this.user = testingService.randomUser();
    }

    @Test
    void shouldRecalculateOnIncomingPointsAfter() {
        testingService.importAndProcess(user, "/data/gpx/20250617.gpx");

        List<ProcessedVisit> processedVisits = currentVisits();
        assertEquals(5, processedVisits.size());

        assertVisit(processedVisits.get(0), "2025-06-16T22:00:09Z", "2025-06-17T05:40:05Z" , MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-17T05:44:08Z", "2025-06-17T05:54:32Z" , ST_THOMAS);
        assertVisit(processedVisits.get(2), "2025-06-17T05:58:10Z", "2025-06-17T13:08:53Z" , MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-17T13:12:31Z", "2025-06-17T13:18:20Z" , ST_THOMAS);
        assertVisit(processedVisits.get(4), "2025-06-17T13:21:28Z", "2025-06-17T21:59:44Z" , MOLTKESTR);

        List<Trip> trips = currenTrips();
        assertEquals(4, trips.size());
        assertTrip(trips.get(0), "2025-06-17T05:40:05Z", "2025-06-17T05:44:08Z", MOLTKESTR, ST_THOMAS);
        assertTrip(trips.get(1), "2025-06-17T05:54:32Z", "2025-06-17T05:58:10Z", ST_THOMAS, MOLTKESTR);
        assertTrip(trips.get(2), "2025-06-17T13:08:53Z", "2025-06-17T13:12:31Z", MOLTKESTR, ST_THOMAS);
        assertTrip(trips.get(3), "2025-06-17T13:18:20Z", "2025-06-17T13:21:28Z", ST_THOMAS, MOLTKESTR);

        testingService.importAndProcess(user, "/data/gpx/20250618.gpx");

        processedVisits = currentVisits();

        assertEquals(10, processedVisits.size());

        assertVisit(processedVisits.get(0), "2025-06-16T22:00:09Z", "2025-06-17T05:40:05Z" , MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-17T05:44:08Z", "2025-06-17T05:54:32Z" , ST_THOMAS);
        assertVisit(processedVisits.get(2), "2025-06-17T05:58:10Z", "2025-06-17T13:08:53Z" , MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-17T13:12:31Z", "2025-06-17T13:18:20Z" , ST_THOMAS);
        assertVisit(processedVisits.get(4), "2025-06-17T13:21:28Z", "2025-06-18T05:45:00Z" , MOLTKESTR);
        assertVisit(processedVisits.get(5), "2025-06-18T05:54:37Z", "2025-06-18T06:02:05Z" , ST_THOMAS);
        assertVisit(processedVisits.get(6), "2025-06-18T06:06:43Z", "2025-06-18T13:01:23Z" , MOLTKESTR);
        assertVisit(processedVisits.get(7), "2025-06-18T13:05:04Z", "2025-06-18T13:13:31Z" , ST_THOMAS);
        assertVisit(processedVisits.get(8), "2025-06-18T13:33:01Z", "2025-06-18T15:50:40Z" , GARTEN);
        assertVisit(processedVisits.get(9), "2025-06-18T16:04:28Z", "2025-06-18T21:59:29Z" , MOLTKESTR);
    }

    @Test
    void shouldRecalculateOnIncomingPointsBefore() {
        testingService.importAndProcess(user, "/data/gpx/20250618.gpx");

        List<ProcessedVisit> processedVisits = currentVisits();
        assertEquals(6, processedVisits.size());

        assertVisit(processedVisits.get(0), "2025-06-17T22:00:15Z", "2025-06-18T05:45:00Z", MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-18T05:54:37Z", "2025-06-18T06:02:05Z", ST_THOMAS);
        assertVisit(processedVisits.get(2), "2025-06-18T06:06:43Z", "2025-06-18T13:01:23Z", MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-18T13:05:04Z", "2025-06-18T13:13:31Z", ST_THOMAS);
        assertVisit(processedVisits.get(4), "2025-06-18T13:33:01Z", "2025-06-18T15:50:40Z", GARTEN);
        assertVisit(processedVisits.get(5), "2025-06-18T16:04:28Z", "2025-06-18T21:59:29Z", MOLTKESTR);

        testingService.importAndProcess(user, "/data/gpx/20250617.gpx");

        Awaitility.await("waiting for import to be finished")
                .logging()
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> currentVisits().size() == 10);

        processedVisits = currentVisits();

        assertEquals(10, processedVisits.size());

        //new visits
        assertVisit(processedVisits.get(0), "2025-06-16T22:00:09Z", "2025-06-17T05:40:05Z", MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-17T05:44:08Z", "2025-06-17T05:54:32Z", ST_THOMAS);
        assertVisit(processedVisits.get(2), "2025-06-17T05:58:10Z", "2025-06-17T13:08:53Z", MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-17T13:12:31Z", "2025-06-17T13:18:20Z", ST_THOMAS);
        assertVisit(processedVisits.get(4), "2025-06-17T13:21:28Z", "2025-06-18T05:45:00Z", MOLTKESTR);
        assertVisit(processedVisits.get(5), "2025-06-18T05:54:37Z", "2025-06-18T06:02:05Z", ST_THOMAS);
        assertVisit(processedVisits.get(6), "2025-06-18T06:06:43Z", "2025-06-18T13:01:23Z", MOLTKESTR);
        assertVisit(processedVisits.get(7), "2025-06-18T13:05:04Z", "2025-06-18T13:13:31Z", ST_THOMAS);
        assertVisit(processedVisits.get(8), "2025-06-18T13:33:01Z", "2025-06-18T15:50:40Z", GARTEN);
        assertVisit(processedVisits.get(9), "2025-06-18T16:04:28Z", "2025-06-18T21:59:29Z", MOLTKESTR);
    }

    @Test
    void shouldCalculateSingleFile() {
        testingService.importAndProcess(user, "/data/gpx/20250618.gpx");

        List<ProcessedVisit> processedVisits = currentVisits();
        assertEquals(6, processedVisits.size());

        assertVisit(processedVisits.get(0), "2025-06-17T22:00:15Z", "2025-06-18T05:45:00Z" , MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-18T05:54:37Z", "2025-06-18T06:02:05Z" , ST_THOMAS);
        assertVisit(processedVisits.get(2), "2025-06-18T06:06:43Z", "2025-06-18T13:01:23Z" , MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-18T13:05:04Z", "2025-06-18T13:13:31Z" , ST_THOMAS);
        assertVisit(processedVisits.get(4), "2025-06-18T13:33:01Z", "2025-06-18T15:50:40Z" , GARTEN);
        assertVisit(processedVisits.get(5), "2025-06-18T16:04:28Z", "2025-06-18T21:59:29Z" , MOLTKESTR);
    }

    @Test
    void shouldCalculateIncludingGapsUnordered() {
        this.testingService.importAndProcess(user, "/data/gpx/overnight-visit-with-gaps/track_1_2025-12-06_081704.gpx");
        this.testingService.importAndProcess(user, "/data/gpx/overnight-visit-with-gaps/track_2_2025-12-06_081704.gpx");

        List<ProcessedVisit> processedVisitsInOrder = currentVisits();

        this.testingService.clearData();

        this.testingService.importAndProcess(user, "/data/gpx/overnight-visit-with-gaps/track_2_2025-12-06_081704.gpx");
        this.testingService.importAndProcess(user, "/data/gpx/overnight-visit-with-gaps/track_1_2025-12-06_081704.gpx");

        List<ProcessedVisit> processedVisitsOutOfOrder = currentVisits();


        for (int i = 0; i < processedVisitsOutOfOrder.size(); i++) {
            ProcessedVisit processedVisit = processedVisitsOutOfOrder.get(i);
            ProcessedVisit processedVisitInOrder = processedVisitsInOrder.get(i);
            assertEquals(processedVisitInOrder.getStartTime(), processedVisit.getStartTime());
            assertEquals(processedVisitInOrder.getEndTime(), processedVisit.getEndTime());
            assertEquals(processedVisitInOrder.getPlace(), processedVisit.getPlace());
        }
    }

    @Test
    void shouldCalculateIncludingGapsWithIncomingData() {
        this.testingService.importAndProcess(user, "/data/gpx/overnight-visit-with-gaps/track_1_2025-12-06_081704.gpx");
        this.testingService.importAndProcess(user, "/data/gpx/overnight-visit-with-gaps/track_2_2025-12-06_081704.gpx");

        List<ProcessedVisit> processedVisitsInOrder = currentVisits();

        this.testingService.clearData();

        this.testingService.importAndProcess(user, "/data/gpx/overnight-visit-with-gaps/track_2_2025-12-06_081704.gpx");
        this.testingService.importAndProcess(user, "/data/gpx/overnight-visit-with-gaps/track_1_2025-12-06_081704.gpx");

        List<ProcessedVisit> processedVisitsOutOfOrder = currentVisits();


        for (int i = 0; i < processedVisitsOutOfOrder.size(); i++) {
            ProcessedVisit processedVisit = processedVisitsOutOfOrder.get(i);
            ProcessedVisit processedVisitInOrder = processedVisitsInOrder.get(i);
            assertEquals(processedVisitInOrder.getStartTime(), processedVisit.getStartTime());
            assertEquals(processedVisitInOrder.getEndTime(), processedVisit.getEndTime());
            assertEquals(processedVisitInOrder.getPlace(), processedVisit.getPlace());
        }
    }

    @Test
    void shouldUpdateLatestLocationForLiveDataOnlyUserThroughFullPipeline() {
        User user = testingService.randomUser();
        User liveDataOnlyUser = user.withUserType(UserType.LIVE_DATA_ONLY);
        userJdbcService.updateUser(liveDataOnlyUser);

        assertTrue(rawLocationPointJdbcService.findLatest(liveDataOnlyUser).isEmpty());

        testingService.importAndProcess(liveDataOnlyUser, "/data/gpx/20250617.gpx");

        Optional<RawLocationPoint> latest = rawLocationPointJdbcService.findLatest(liveDataOnlyUser);
        assertTrue(latest.isPresent());
        assertTrue(latest.get().getTimestamp().isBefore(Instant.parse("2025-06-18T00:00:00Z")),
                "latest should be from June 17 data, got " + latest.get().getTimestamp());

        testingService.importAndProcess(liveDataOnlyUser, "/data/gpx/20250618.gpx");

        latest = rawLocationPointJdbcService.findLatest(liveDataOnlyUser);
        assertTrue(latest.isPresent());
        assertTrue(latest.get().getTimestamp().isAfter(Instant.parse("2025-06-18T00:00:00Z")),
                "latest should have advanced to June 18 data, got " + latest.get().getTimestamp());

        testingService.importAndProcess(liveDataOnlyUser, "/data/gpx/20250617.gpx");

        latest = rawLocationPointJdbcService.findLatest(liveDataOnlyUser);
        assertTrue(latest.isPresent());
        assertTrue(latest.get().getTimestamp().isAfter(Instant.parse("2025-06-18T00:00:00Z")),
                "latest should remain on June 18 after re-importing older data, got " + latest.get().getTimestamp());
    }

    @Test
    void shouldKeepOnlyLatestDataForLiveDataOnlyUserViaOwntracksFlow() {
        User user = testingService.randomUser();
        User liveDataOnlyUser = user.withUserType(UserType.LIVE_DATA_ONLY);
        userJdbcService.updateUser(liveDataOnlyUser);
        Device device = testingService.findDefaultDevice(liveDataOnlyUser);

        assertTrue(rawLocationPointJdbcService.findLatest(liveDataOnlyUser).isEmpty());

        LocationPoint point1 = new LocationPoint();
        point1.setLatitude(60.1699);
        point1.setLongitude(24.9384);
        point1.setTimestamp(Instant.parse("2025-06-17T10:00:00Z"));
        point1.setAccuracyMeters(5.0);
        locationBatchingService.addLocationPoint(liveDataOnlyUser, device, point1);

        LocationPoint point2 = new LocationPoint();
        point2.setLatitude(60.1705);
        point2.setLongitude(24.9410);
        point2.setTimestamp(Instant.parse("2025-06-18T11:00:00Z"));
        point2.setAccuracyMeters(3.0);
        locationBatchingService.addLocationPoint(liveDataOnlyUser, device, point2);

        LocationPoint point3 = new LocationPoint();
        point3.setLatitude(60.1600);
        point3.setLongitude(24.9300);
        point3.setTimestamp(Instant.parse("2025-06-17T12:00:00Z"));
        point3.setAccuracyMeters(4.0);
        locationBatchingService.addLocationPoint(liveDataOnlyUser, device, point3);

        Awaitility.await("waiting for batch to flush and pipeline to process")
                .atMost(60, TimeUnit.SECONDS)
                .until(() -> rawLocationPointJdbcService.findLatest(liveDataOnlyUser).isPresent());

        Optional<RawLocationPoint> latest = rawLocationPointJdbcService.findLatest(liveDataOnlyUser);
        assertTrue(latest.isPresent());
        assertEquals(60.1705, latest.get().getLatitude(), 0.0001,
                "latest should be point2 (June 18), got lat " + latest.get().getLatitude());
        assertTrue(latest.get().getTimestamp().isAfter(Instant.parse("2025-06-18T00:00:00Z")),
                "latest timestamp should be from June 18, got " + latest.get().getTimestamp());

        Optional<SourceLocationPoint> latestSource = sourceLocationPointJdbcService.findLatest(liveDataOnlyUser, device);
        assertTrue(latestSource.isPresent());
        assertTrue(latestSource.get().getTimestamp().isAfter(Instant.parse("2025-06-18T00:00:00Z")),
                "source points should keep only the chronologically latest, got " + latestSource.get().getTimestamp());
    }

    private List<ProcessedVisit> currentVisits() {
        return this.processedVisitJdbcService.findByUser(this.user);
    }

    private List<Trip> currenTrips() {
        return this.tripJdbcService.findByUser(this.user);
    }

}
