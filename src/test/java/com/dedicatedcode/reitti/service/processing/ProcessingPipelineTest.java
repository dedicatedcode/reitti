package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.dedicatedcode.reitti.TestConstants.Points.*;
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

        assertVisit(processedVisits.get(0), "2025-06-16T22:00:09.154Z", "2025-06-17T05:39:50.330Z" , MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-17T05:44:08.763Z", "2025-06-17T05:49:18.965Z" , ST_THOMAS);
        assertVisit(processedVisits.get(2), "2025-06-17T05:58:10.797Z", "2025-06-17T13:08:53.346Z" , MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-17T13:12:33.214Z", "2025-06-17T13:18:20.778Z" , ST_THOMAS);
        assertVisit(processedVisits.get(4), "2025-06-17T13:21:28.334Z", "2025-06-17T21:59:44.876Z" , MOLTKESTR);

        List<Trip> trips = currenTrips();
        assertEquals(4, trips.size());
        assertTrip(trips.get(0), "2025-06-17T05:39:50.330Z","2025-06-17T05:44:08.763Z", MOLTKESTR, ST_THOMAS);
        assertTrip(trips.get(1), "2025-06-17T05:49:18.965Z","2025-06-17T05:58:10.797Z", ST_THOMAS, MOLTKESTR);
        assertTrip(trips.get(2), "2025-06-17T13:08:53.346Z","2025-06-17T13:12:33.214Z", MOLTKESTR, ST_THOMAS);
        assertTrip(trips.get(3), "2025-06-17T13:18:20.778Z","2025-06-17T13:21:28.334Z", ST_THOMAS, MOLTKESTR);

        testingService.importAndProcess(user, "/data/gpx/20250618.gpx");

        processedVisits = currentVisits();

        assertEquals(10, processedVisits.size());

        assertVisit(processedVisits.get(0), "2025-06-16T22:00:09.154Z", "2025-06-17T05:39:50.330Z" , MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-17T05:44:08.763Z", "2025-06-17T05:49:18.965Z" , ST_THOMAS);
        assertVisit(processedVisits.get(2), "2025-06-17T05:58:10.797Z", "2025-06-17T13:08:53.346Z" , MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-17T13:12:33.214Z", "2025-06-17T13:18:20.778Z" , ST_THOMAS);
        assertVisit(processedVisits.get(4), "2025-06-17T13:21:28.334Z", "2025-06-18T05:45:00.682Z", MOLTKESTR);
        assertVisit(processedVisits.get(5), "2025-06-18T05:55:09.648Z", "2025-06-18T06:02:05.400Z", ST_THOMAS);
        assertVisit(processedVisits.get(6), "2025-06-18T06:06:43.274Z", "2025-06-18T13:01:23.419Z", MOLTKESTR);
        assertVisit(processedVisits.get(7), "2025-06-18T13:05:04.278Z", "2025-06-18T13:13:31Z"    , ST_THOMAS);
        assertVisit(processedVisits.get(8), "2025-06-18T13:33:35.626Z", "2025-06-18T15:50:40Z"    , GARTEN);
        assertVisit(processedVisits.get(9), "2025-06-18T16:05:49.301Z", "2025-06-18T21:59:29.055Z", MOLTKESTR);
    }

    @Test
    void shouldRecalculateOnIncomingPointsBefore() {
        testingService.importAndProcess(user, "/data/gpx/20250618.gpx");

        List<ProcessedVisit> processedVisits = currentVisits();
        assertEquals(6, processedVisits.size());

        assertVisit(processedVisits.get(0), "2025-06-17T22:00:15.843Z","2025-06-18T05:45:00.682Z", MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-18T05:55:09.648Z","2025-06-18T06:02:05.400Z", ST_THOMAS);
        assertVisit(processedVisits.get(2), "2025-06-18T06:06:43.274Z","2025-06-18T13:01:23.419Z", MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-18T13:05:04.278Z","2025-06-18T13:13:31Z"    , ST_THOMAS);
        assertVisit(processedVisits.get(4), "2025-06-18T13:33:35.626Z","2025-06-18T15:50:40Z"    , GARTEN);
        assertVisit(processedVisits.get(5), "2025-06-18T16:05:49.301Z","2025-06-18T21:59:29.055Z", MOLTKESTR);

        testingService.importAndProcess(user, "/data/gpx/20250617.gpx");

        Awaitility.await("waiting for import to be finished")
                .logging()
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> currentVisits().size() == 10);

        processedVisits = currentVisits();

        assertEquals(10, processedVisits.size());

        //new visits
        assertVisit(processedVisits.get(0),"2025-06-16T22:00:09.154Z", "2025-06-17T05:39:50.330Z", MOLTKESTR);
        assertVisit(processedVisits.get(1),"2025-06-17T05:44:08.763Z", "2025-06-17T05:49:18.965Z", ST_THOMAS);
        assertVisit(processedVisits.get(2),"2025-06-17T05:58:10.797Z", "2025-06-17T13:08:53.346Z", MOLTKESTR);
        assertVisit(processedVisits.get(3),"2025-06-17T13:12:33.214Z", "2025-06-17T13:18:20.778Z", ST_THOMAS);
        assertVisit(processedVisits.get(4),"2025-06-17T13:21:28.334Z", "2025-06-18T05:45:00.682Z", MOLTKESTR);
        assertVisit(processedVisits.get(5),"2025-06-18T05:55:09.648Z", "2025-06-18T06:02:05.400Z", ST_THOMAS);
        assertVisit(processedVisits.get(6),"2025-06-18T06:06:43.274Z", "2025-06-18T13:01:23.419Z", MOLTKESTR);
        assertVisit(processedVisits.get(7),"2025-06-18T13:05:04.278Z", "2025-06-18T13:13:31Z"    , ST_THOMAS);
        assertVisit(processedVisits.get(8),"2025-06-18T13:33:35.626Z", "2025-06-18T15:50:40Z"    , GARTEN);
        assertVisit(processedVisits.get(9),"2025-06-18T16:05:49.301Z", "2025-06-18T21:59:29.055Z", MOLTKESTR);
    }

    @Test
    void shouldCalculateSingleFile() {
        testingService.importAndProcess(user, "/data/gpx/20250618.gpx");

        List<ProcessedVisit> processedVisits = currentVisits();
        assertEquals(6, processedVisits.size());

        assertVisit(processedVisits.get(0), "2025-06-17T22:00:15.843Z","2025-06-18T05:45:00.682Z" , MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-18T05:55:09.648Z","2025-06-18T06:02:05.400Z" , ST_THOMAS);
        assertVisit(processedVisits.get(2), "2025-06-18T06:06:43.274Z","2025-06-18T13:01:23.419Z" , MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-18T13:05:04.278Z","2025-06-18T13:13:31Z"     , ST_THOMAS);
        assertVisit(processedVisits.get(4), "2025-06-18T13:33:35.626Z","2025-06-18T15:50:40Z"     , GARTEN);
        assertVisit(processedVisits.get(5), "2025-06-18T16:05:49.301Z","2025-06-18T21:59:29.055Z" , MOLTKESTR);
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
    void shouldCalculateIncludingGapsWithIncomingData() throws InterruptedException {
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
    private static void assertVisit(ProcessedVisit processedVisit, String startTime, String endTime, GeoPoint location) {
        assertEquals(Instant.parse(startTime).truncatedTo(ChronoUnit.SECONDS), processedVisit.getStartTime().truncatedTo(ChronoUnit.SECONDS));
        assertEquals(Instant.parse(endTime).truncatedTo(ChronoUnit.SECONDS), processedVisit.getEndTime().truncatedTo(ChronoUnit.SECONDS));
        GeoPoint currentLocation = new GeoPoint(processedVisit.getPlace().getLatitudeCentroid(), processedVisit.getPlace().getLongitudeCentroid());
        assertTrue(location.near(currentLocation), "Locations are not near to each other. \nExpected [" + currentLocation + "] to be in range \nto [" + location + "]");
    }

    private List<ProcessedVisit> currentVisits() {
        return this.processedVisitJdbcService.findByUser(this.user);
    }

    private List<Trip> currenTrips() {
        return this.tripJdbcService.findByUser(this.user);
    }

    private static void assertTrip(Trip trip, String startTime, String endTime, GeoPoint startLocation, GeoPoint endLocation) {
        assertEquals(Instant.parse(startTime).truncatedTo(ChronoUnit.SECONDS), trip.getStartTime().truncatedTo(ChronoUnit.SECONDS));
        assertEquals(Instant.parse(endTime).truncatedTo(ChronoUnit.SECONDS), trip.getEndTime().truncatedTo(ChronoUnit.SECONDS));
        
        GeoPoint actualStartLocation = GeoPoint.from(trip.getStartVisit().getPlace().getLatitudeCentroid(), trip.getStartVisit().getPlace().getLongitudeCentroid());
        assertTrue(startLocation.near(actualStartLocation), 
            "Start locations are not near to each other. \nExpected [" + actualStartLocation + "] to be in range \nto [" + startLocation + "]");
        
        GeoPoint actualEndLocation = GeoPoint.from(trip.getEndVisit().getPlace().getLatitudeCentroid(), trip.getEndVisit().getPlace().getLongitudeCentroid());
        assertTrue(endLocation.near(actualEndLocation), 
            "End locations are not near to each other. \nExpected [" + actualEndLocation + "] to be in range \nto [" + endLocation + "]");
    }
}
