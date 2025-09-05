package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.Trip;
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

    @BeforeEach
    public void setUp() {
        this.testingService.clearData();
    }

    @Test
    void shouldRecalculateOnIncomingPointsAfter() {
        testingService.importAndProcess("/data/gpx/20250617.gpx");

        List<ProcessedVisit> processedVisits = currentVisits();
        assertEquals(5, processedVisits.size());

        assertVisit(processedVisits.get(0), "2025-06-16T22:00:09.154Z", "2025-06-17T05:39:50.330Z", MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-17T05:44:39.578Z", "2025-06-17T05:54:32.974Z", ST_THOMAS);
        assertVisit(processedVisits.get(2), "2025-06-17T05:58:10.797Z", "2025-06-17T13:08:53.346Z", MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-17T13:12:33.214Z", "2025-06-17T13:18:20.778Z", ST_THOMAS);
        assertVisit(processedVisits.get(4), "2025-06-17T13:22:00.725Z", "2025-06-17T21:59:44.876Z", MOLTKESTR);

        List<Trip> trips = currenTrips();
        assertEquals(4, trips.size());
        assertTrip(trips.get(0), "2025-06-17T05:39:50.330Z", MOLTKESTR, "2025-06-17T05:44:39.578Z", ST_THOMAS);
        assertTrip(trips.get(1), "2025-06-17T05:54:32.974Z", ST_THOMAS, "2025-06-17T05:58:10.797Z", MOLTKESTR);
        assertTrip(trips.get(2), "2025-06-17T13:08:53.346Z", MOLTKESTR, "2025-06-17T13:12:33.214Z", ST_THOMAS);
        assertTrip(trips.get(3), "2025-06-17T13:18:20.778Z", ST_THOMAS, "2025-06-17T13:22:00.725Z", MOLTKESTR);
        
        testingService.importAndProcess("/data/gpx/20250618.gpx");

        processedVisits = currentVisits();

        assertEquals(10, processedVisits.size());

        //should not touch visits before the new data
        assertVisit(processedVisits.get(0), "2025-06-16T22:00:09.154Z", "2025-06-17T05:39:50.330Z", MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-17T05:44:39.578Z", "2025-06-17T05:54:32.974Z", ST_THOMAS);
        assertVisit(processedVisits.get(2), "2025-06-17T05:58:10.797Z", "2025-06-17T13:08:53.346Z", MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-17T13:12:33.214Z", "2025-06-17T13:18:20.778Z", ST_THOMAS);

        //should extend the last visit of the old day
        assertVisit(processedVisits.get(4), "2025-06-17T13:22:00.725Z", "2025-06-18T05:45:00.682Z", MOLTKESTR);

        //new visits
        assertVisit(processedVisits.get(5), "2025-06-18T05:55:09.648Z","2025-06-18T06:02:05.400Z", ST_THOMAS);
        assertVisit(processedVisits.get(6), "2025-06-18T06:06:43.274Z","2025-06-18T13:01:23.419Z", MOLTKESTR);
        assertVisit(processedVisits.get(7), "2025-06-18T13:05:04.278Z","2025-06-18T13:13:16.416Z", ST_THOMAS);
        assertVisit(processedVisits.get(8), "2025-06-18T13:34:07Z","2025-06-18T15:50:40Z", GARTEN);
        assertVisit(processedVisits.get(9), "2025-06-18T16:05:49.301Z","2025-06-18T21:59:29.055Z", MOLTKESTR);

        trips = currenTrips();
        assertEquals(9, trips.size());
        assertTrip(trips.get(0), "2025-06-17T05:39:50.330Z", MOLTKESTR, "2025-06-17T05:44:39.578Z", ST_THOMAS);
        assertTrip(trips.get(1), "2025-06-17T05:54:32.974Z", ST_THOMAS, "2025-06-17T05:58:10.797Z", MOLTKESTR);
        assertTrip(trips.get(2), "2025-06-17T13:08:53.346Z", MOLTKESTR, "2025-06-17T13:12:33.214Z", ST_THOMAS);
        assertTrip(trips.get(3), "2025-06-17T13:18:20.778Z", ST_THOMAS, "2025-06-17T13:22:00.725Z", MOLTKESTR);
        assertTrip(trips.get(4), "2025-06-18T05:45:00.682Z", MOLTKESTR, "2025-06-18T05:55:09.648Z", ST_THOMAS);
        assertTrip(trips.get(5), "2025-06-18T06:02:05.400Z", ST_THOMAS, "2025-06-18T06:06:43.274Z", MOLTKESTR);
        assertTrip(trips.get(6), "2025-06-18T13:01:23.419Z", MOLTKESTR, "2025-06-18T13:05:04.278Z", ST_THOMAS);
        assertTrip(trips.get(7), "2025-06-18T13:13:16.416Z", ST_THOMAS, "2025-06-18T13:34:07Z", GARTEN);
        assertTrip(trips.get(8), "2025-06-18T15:50:40Z", GARTEN, "2025-06-18T16:05:49.301Z", MOLTKESTR);

    }

    @Test
    void shouldRecalculateOnIncomingPointsBefore() {
        testingService.importAndProcess("/data/gpx/20250618.gpx");

        List<ProcessedVisit> processedVisits = currentVisits();
        assertEquals(6, processedVisits.size());
        assertVisit(processedVisits.get(0), "2025-06-17T22:00:15.843Z", "2025-06-18T05:45:00.682Z", MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-18T05:55:09.648Z","2025-06-18T06:02:05.400Z", ST_THOMAS);
        assertVisit(processedVisits.get(2), "2025-06-18T06:06:43.274Z","2025-06-18T13:01:23.419Z", MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-18T13:05:04.278Z","2025-06-18T13:13:16.416Z", ST_THOMAS);
        assertVisit(processedVisits.get(4), "2025-06-18T13:34:07Z","2025-06-18T15:50:40Z", GARTEN);
        assertVisit(processedVisits.get(5), "2025-06-18T16:05:49.301Z","2025-06-18T21:59:29.055Z", MOLTKESTR);

        testingService.importAndProcess("/data/gpx/20250617.gpx");

        processedVisits = currentVisits();

        assertEquals(10, processedVisits.size());

        //should not touch visits before the new data
        assertVisit(processedVisits.get(0), "2025-06-16T22:00:09.154Z", "2025-06-17T05:39:50.330Z", MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-17T05:44:39.578Z", "2025-06-17T05:54:32.974Z", ST_THOMAS);
        assertVisit(processedVisits.get(2), "2025-06-17T05:58:10.797Z", "2025-06-17T13:08:53.346Z", MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-17T13:12:33.214Z", "2025-06-17T13:18:20.778Z", ST_THOMAS);

        //should extend the first visit of the old day
        assertVisit(processedVisits.get(4), "2025-06-17T13:22:00.725Z", "2025-06-18T05:45:00.682Z", MOLTKESTR);

        //new visits
        assertVisit(processedVisits.get(5), "2025-06-18T05:55:09.648Z","2025-06-18T06:02:05.400Z", ST_THOMAS);
        assertVisit(processedVisits.get(6), "2025-06-18T06:06:43.274Z","2025-06-18T13:01:23.419Z", MOLTKESTR);
        assertVisit(processedVisits.get(7), "2025-06-18T13:05:04.278Z","2025-06-18T13:13:16.416Z", ST_THOMAS);
        assertVisit(processedVisits.get(8), "2025-06-18T13:34:07Z","2025-06-18T15:50:40Z", GARTEN);
        assertVisit(processedVisits.get(9), "2025-06-18T16:05:49.301Z","2025-06-18T21:59:29.055Z", MOLTKESTR);
    }

    private static void assertVisit(ProcessedVisit processedVisit, String startTime, String endTime, GeoPoint location) {
        assertEquals(Instant.parse(startTime), processedVisit.getStartTime());
        assertEquals(Instant.parse(endTime), processedVisit.getEndTime());
        GeoPoint currentLocation = new GeoPoint(processedVisit.getPlace().getLatitudeCentroid(), processedVisit.getPlace().getLongitudeCentroid());
        assertTrue(location.near(currentLocation), "Locations are not near to each other. \nExpected [" + currentLocation + "] to be in range \nto [" + location + "]");
    }

    private List<ProcessedVisit> currentVisits() {
        return this.processedVisitJdbcService.findByUser(testingService.admin());
    }

    private List<Trip> currenTrips() {
        return this.tripJdbcService.findByUser(testingService.admin());
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
