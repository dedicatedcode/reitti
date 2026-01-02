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
import java.time.temporal.ChronoUnit;
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
        assertEquals(4, processedVisits.size());

        assertVisit(processedVisits.get(0), "2025-06-16T22:00:09Z", "2025-06-17T05:40:26Z" , MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-17T05:58:10Z", "2025-06-17T13:08:53Z" , MOLTKESTR);
        assertVisit(processedVisits.get(2), "2025-06-17T13:12:33Z", "2025-06-17T13:18:20Z" , ST_THOMAS);
        assertVisit(processedVisits.get(3), "2025-06-17T13:21:28Z", "2025-06-17T21:59:44Z" , MOLTKESTR);

        List<Trip> trips = currenTrips();
        assertEquals(3, trips.size());
        assertTrip(trips.get(0), "2025-06-17T05:40:26Z","2025-06-17T05:58:10Z", MOLTKESTR, MOLTKESTR);
        assertTrip(trips.get(1), "2025-06-17T13:08:53Z","2025-06-17T13:12:33Z", MOLTKESTR, ST_THOMAS);
        assertTrip(trips.get(2), "2025-06-17T13:18:20Z","2025-06-17T13:21:28Z", ST_THOMAS, MOLTKESTR);

        testingService.importAndProcess(user, "/data/gpx/20250618.gpx");

        processedVisits = currentVisits();

        assertEquals(11, processedVisits.size());

        //should not touch visits before the new data
        assertVisit(processedVisits.get(0), "2025-06-16T22:00:09Z", "2025-06-17T05:42:33Z" , MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-17T05:43:05Z", "2025-06-17T05:55:34Z" , ST_THOMAS);
        assertVisit(processedVisits.get(2), "2025-06-17T05:55:56Z", "2025-06-17T13:11:30Z" , MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-17T13:12:01Z", "2025-06-17T13:18:51Z" , ST_THOMAS);

        //should extend the last visit of the old day
        assertVisit(processedVisits.get(4), "2025-06-17T13:21:28Z", "2025-06-18T05:45:36Z", MOLTKESTR);

        //new visits
        assertVisit(processedVisits.get(5) , "2025-06-18T05:46:10Z", "2025-06-18T05:53:01Z", MOLTKEPLATZ);
        assertVisit(processedVisits.get(6) , "2025-06-18T05:54:37Z", "2025-06-18T06:02:05Z", ST_THOMAS);
        assertVisit(processedVisits.get(7) , "2025-06-18T06:05:07Z", "2025-06-18T13:01:23Z", MOLTKESTR);
        assertVisit(processedVisits.get(8) , "2025-06-18T13:05:04Z", "2025-06-18T13:13:47Z", ST_THOMAS);
        assertVisit(processedVisits.get(9) , "2025-06-18T13:33:35Z", "2025-06-18T15:50:40Z", GARTEN);
        assertVisit(processedVisits.get(10), "2025-06-18T16:03:09Z", "2025-06-18T21:59:29Z", MOLTKESTR);
    }

    @Test
    void shouldRecalculateOnIncomingPointsBefore() {
        testingService.importAndProcess(user, "/data/gpx/20250618.gpx");

        List<ProcessedVisit> processedVisits = currentVisits();
        assertEquals(6, processedVisits.size());

        assertVisit(processedVisits.get(0), "2025-06-17T22:00:15Z","2025-06-18T05:45:36Z", MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-18T05:54:37Z","2025-06-18T06:02:05Z", ST_THOMAS);
        assertVisit(processedVisits.get(2), "2025-06-18T06:05:07Z","2025-06-18T13:01:23Z", MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-18T13:05:04Z","2025-06-18T13:13:47Z", ST_THOMAS);
        assertVisit(processedVisits.get(4), "2025-06-18T13:33:35Z","2025-06-18T15:50:40Z", GARTEN);
        assertVisit(processedVisits.get(5), "2025-06-18T16:03:09Z","2025-06-18T21:59:29Z", MOLTKESTR);

        testingService.importAndProcess(user, "/data/gpx/20250617.gpx");

        processedVisits = currentVisits();

        assertEquals(10, processedVisits.size());

        //new visits
        assertVisit(processedVisits.get(0),"2025-06-16T22:00:09Z", "2025-06-17T05:42:33Z", MOLTKESTR);
        assertVisit(processedVisits.get(1),"2025-06-17T05:43:05Z", "2025-06-17T05:55:34Z", ST_THOMAS);
        assertVisit(processedVisits.get(2),"2025-06-17T05:55:56Z", "2025-06-17T13:11:30Z", MOLTKESTR);
        assertVisit(processedVisits.get(3),"2025-06-17T13:12:01Z", "2025-06-17T13:18:51Z", ST_THOMAS);

        //should extend the last visit of the old day
        assertVisit(processedVisits.get(4),"2025-06-17T13:19:22Z", "2025-06-18T05:53:01Z", MOLTKESTR);

        //should not touch visits after the new data
        assertVisit(processedVisits.get(5),"2025-06-18T05:53:33Z", "2025-06-18T06:02:05Z", ST_THOMAS);
        assertVisit(processedVisits.get(6),"2025-06-18T06:03:00Z", "2025-06-18T13:04:01Z", MOLTKESTR);
        assertVisit(processedVisits.get(7),"2025-06-18T13:04:33Z", "2025-06-18T13:13:47Z", ST_THOMAS);
        assertVisit(processedVisits.get(8),"2025-06-18T13:33:35Z", "2025-06-18T15:50:40Z", GARTEN);
        assertVisit(processedVisits.get(9),"2025-06-18T16:02:02Z", "2025-06-18T21:59:29Z", MOLTKESTR);
    }

    @Test
    void shouldCalculateSingleFile() {
        testingService.importAndProcess(user, "/data/gpx/20250618.gpx");

        List<ProcessedVisit> processedVisits = currentVisits();
        assertEquals(6, processedVisits.size());

        assertVisit(processedVisits.get(0), "2025-06-17T22:00:15Z","2025-06-18T05:45:36Z" , MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-18T05:54:37Z","2025-06-18T06:02:05Z" , ST_THOMAS);
        assertVisit(processedVisits.get(2), "2025-06-18T06:05:07Z","2025-06-18T13:01:23Z" , MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-18T13:05:04Z","2025-06-18T13:13:47Z" , ST_THOMAS);
        assertVisit(processedVisits.get(4), "2025-06-18T13:33:35Z","2025-06-18T15:50:40Z" , GARTEN);
        assertVisit(processedVisits.get(5), "2025-06-18T16:03:09Z","2025-06-18T21:59:29Z" , MOLTKESTR);
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
        this.testingService.processWhileImport(user, "/data/gpx/overnight-visit-with-gaps/track_2_2025-12-06_081704.gpx");

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
