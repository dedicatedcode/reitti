package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

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

        assertVisit(processedVisits.get(0), "2025-06-16T22:00:09.154Z", "2025-06-17T05:40:26Z", MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-17T05:43:05.951Z", "2025-06-17T05:55:03.792Z", ST_THOMAS);
        assertVisit(processedVisits.get(2), "2025-06-17T05:57:41Z",     "2025-06-17T13:09:29Z", MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-17T13:12:01.542Z", "2025-06-17T13:18:51.590Z", ST_THOMAS);
        assertVisit(processedVisits.get(4), "2025-06-17T13:21:28.334Z", "2025-06-17T21:59:44.876Z", MOLTKESTR);

        List<Trip> trips = currenTrips();
        assertEquals(4, trips.size());
        assertTrip(trips.get(0), "2025-06-17T05:40:26Z"     , MOLTKESTR, "2025-06-17T05:43:05.951Z", ST_THOMAS);
        assertTrip(trips.get(1), "2025-06-17T05:55:03.792Z" , ST_THOMAS, "2025-06-17T05:57:41Z", MOLTKESTR);
        assertTrip(trips.get(2), "2025-06-17T13:09:29Z"     , MOLTKESTR, "2025-06-17T13:12:01.542Z", ST_THOMAS);
        assertTrip(trips.get(3), "2025-06-17T13:18:51.590Z" , ST_THOMAS, "2025-06-17T13:21:28.334Z", MOLTKESTR);

        testingService.importAndProcess(user, "/data/gpx/20250618.gpx");

        processedVisits = currentVisits();

        assertEquals(10, processedVisits.size());

        //should not touch visits before the new data
        assertVisit(processedVisits.get(0), "2025-06-16T22:00:09.154Z", "2025-06-17T05:40:26Z", MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-17T05:43:37.962Z", "2025-06-17T05:49:18.965Z", ST_THOMAS);
        assertVisit(processedVisits.get(2), "2025-06-17T05:57:41Z", "2025-06-17T13:09:29Z", MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-17T13:12:33.214Z", "2025-06-17T13:18:20.778Z", ST_THOMAS);

        //should extend the last visit of the old day
        assertVisit(processedVisits.get(4), "2025-06-17T13:21:28.334Z", "2025-06-18T05:45:36Z", MOLTKESTR);

        //new visits
        assertVisit(processedVisits.get(5), "2025-06-18T05:55:09.648Z","2025-06-18T06:02:05.400Z", ST_THOMAS);
        assertVisit(processedVisits.get(6), "2025-06-18T06:05:07.755Z","2025-06-18T13:01:23.419Z", MOLTKESTR);
        assertVisit(processedVisits.get(7), "2025-06-18T13:05:04.278Z","2025-06-18T13:13:31Z", ST_THOMAS);
        assertVisit(processedVisits.get(8), "2025-06-18T13:34:07Z","2025-06-18T15:50:40Z", GARTEN);
        assertVisit(processedVisits.get(9), "2025-06-18T16:03:09.866Z","2025-06-18T21:59:29.055Z", MOLTKESTR);

        trips = currenTrips();
        assertEquals(9, trips.size());
        assertTrip(trips.get(0), "2025-06-17T05:40:26Z", MOLTKESTR, "2025-06-17T05:43:37.962Z", ST_THOMAS);
        assertTrip(trips.get(1), "2025-06-17T05:49:18.965Z", ST_THOMAS, "2025-06-17T05:57:41Z", MOLTKESTR);
        assertTrip(trips.get(2), "2025-06-17T13:09:29Z", MOLTKESTR, "2025-06-17T13:12:33.214Z", ST_THOMAS);
        assertTrip(trips.get(3), "2025-06-17T13:18:20.778Z", ST_THOMAS, "2025-06-17T13:21:28.334Z", MOLTKESTR);
        assertTrip(trips.get(4), "2025-06-18T05:45:36Z", MOLTKESTR, "2025-06-18T05:55:09.648Z", ST_THOMAS);
        assertTrip(trips.get(5), "2025-06-18T06:02:05.400Z", ST_THOMAS, "2025-06-18T06:05:07.755Z", MOLTKESTR);
        assertTrip(trips.get(6), "2025-06-18T13:01:23.419Z", MOLTKESTR, "2025-06-18T13:05:04.278Z", ST_THOMAS);
        assertTrip(trips.get(7), "2025-06-18T13:13:31Z", ST_THOMAS, "2025-06-18T13:34:07Z", GARTEN);
        assertTrip(trips.get(8), "2025-06-18T15:50:40Z", GARTEN, "2025-06-18T16:03:09.866Z", MOLTKESTR);
    }

    @Test
    void shouldRecalculateOnIncomingPointsBefore() {
        testingService.importAndProcess(user, "/data/gpx/20250618.gpx");

        List<ProcessedVisit> processedVisits = currentVisits();
        assertEquals(6, processedVisits.size());

        assertVisit(processedVisits.get(0), "2025-06-17T22:00:15.843Z", "2025-06-18T05:46:43Z", MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-18T05:53:33.667Z","2025-06-18T06:01:54.440Z", ST_THOMAS);
        assertVisit(processedVisits.get(2), "2025-06-18T06:04:36Z","2025-06-18T13:01:57Z", MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-18T13:04:33.424Z","2025-06-18T13:13:47.443Z", ST_THOMAS);
        assertVisit(processedVisits.get(4), "2025-06-18T13:33:05Z","2025-06-18T15:50:40Z", GARTEN);
        assertVisit(processedVisits.get(5), "2025-06-18T16:02:38Z","2025-06-18T21:59:29.055Z", MOLTKESTR);

        testingService.importAndProcess(user, "/data/gpx/20250617.gpx");

        processedVisits = currentVisits();

        assertEquals(10, processedVisits.size());

        //new visits
        assertVisit(processedVisits.get(0), "2025-06-16T22:00:09.154Z", "2025-06-17T05:41:00Z", MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-17T05:41:30.989Z", "2025-06-17T05:57:07.729Z", ST_THOMAS);
        assertVisit(processedVisits.get(2), "2025-06-17T05:57:41Z", "2025-06-17T13:09:29Z", MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-17T13:09:51.476Z", "2025-06-17T13:20:24.494Z", ST_THOMAS);

        //should extend the first visit of the old day
        assertVisit(processedVisits.get(4), "2025-06-17T13:20:58Z", "2025-06-18T05:46:43Z", MOLTKESTR);

        //should not touch visits after the new data
        assertVisit(processedVisits.get(5), "2025-06-18T05:47:13.682Z","2025-06-18T06:04:02.435Z", ST_THOMAS);
        assertVisit(processedVisits.get(6), "2025-06-18T06:04:36Z","2025-06-18T13:01:57Z", MOLTKESTR);
        assertVisit(processedVisits.get(7), "2025-06-18T13:02:27.656Z","2025-06-18T13:14:19.417Z", ST_THOMAS);
        assertVisit(processedVisits.get(8), "2025-06-18T13:33:05Z","2025-06-18T15:50:40Z", GARTEN);
        assertVisit(processedVisits.get(9), "2025-06-18T16:02:38Z","2025-06-18T21:59:29.055Z", MOLTKESTR);
    }

    @Test
    void shouldCalculateSingleFile() {
        testingService.importAndProcess(user, "/data/gpx/20250618.gpx");

        List<ProcessedVisit> processedVisits = currentVisits();
        assertEquals(6, processedVisits.size());

        assertVisit(processedVisits.get(0), "2025-06-17T22:00:15.843Z", "2025-06-18T05:46:43Z", MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-18T05:53:33.667Z","2025-06-18T06:01:54.440Z", ST_THOMAS);
        assertVisit(processedVisits.get(2), "2025-06-18T06:04:36Z","2025-06-18T13:01:57Z", MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-18T13:04:33.424Z","2025-06-18T13:13:47.443Z", ST_THOMAS);
        assertVisit(processedVisits.get(4), "2025-06-18T13:33:05Z","2025-06-18T15:50:40Z", GARTEN);
        assertVisit(processedVisits.get(5), "2025-06-18T16:02:38Z","2025-06-18T21:59:29.055Z", MOLTKESTR);
    }

    private record ExpectedVisit(String range, GeoPoint location) {}

    private static void assertVisit(ProcessedVisit processedVisit, String startTime, String endTime, GeoPoint location) {
        assertEquals(Instant.parse(startTime), processedVisit.getStartTime());
        assertEquals(Instant.parse(endTime), processedVisit.getEndTime());
        GeoPoint currentLocation = new GeoPoint(processedVisit.getPlace().getLatitudeCentroid(), processedVisit.getPlace().getLongitudeCentroid());
        assertTrue(location.near(currentLocation), "Locations are not near to each other. \nExpected [" + currentLocation + "] to be in range \nto [" + location + "]");
    }

    private List<ProcessedVisit> currentVisits() {
        return this.processedVisitJdbcService.findByUser(this.user);
    }

    private List<Trip> currenTrips() {
        return this.tripJdbcService.findByUser(this.user);
    }

    private static void assertTrip(Trip trip, String startTime, GeoPoint startLocation, String endTime, GeoPoint endLocation) {
        assertEquals(Instant.parse(startTime), trip.getStartTime());
        assertEquals(Instant.parse(endTime), trip.getEndTime());
        
        GeoPoint actualStartLocation = GeoPoint.from(trip.getStartVisit().getPlace().getLatitudeCentroid(), trip.getStartVisit().getPlace().getLongitudeCentroid());
        assertTrue(startLocation.near(actualStartLocation), 
            "Start locations are not near to each other. \nExpected [" + actualStartLocation + "] to be in range \nto [" + startLocation + "]");
        
        GeoPoint actualEndLocation = GeoPoint.from(trip.getEndVisit().getPlace().getLatitudeCentroid(), trip.getEndVisit().getPlace().getLongitudeCentroid());
        assertTrue(endLocation.near(actualEndLocation), 
            "End locations are not near to each other. \nExpected [" + actualEndLocation + "] to be in range \nto [" + endLocation + "]");
    }
}
