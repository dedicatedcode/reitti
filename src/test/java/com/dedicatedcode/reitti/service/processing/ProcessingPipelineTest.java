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
        assertEquals(5, processedVisits.size());

        assertVisit(processedVisits.get(0), "2025-06-16T22:01:43Z","2025-06-17T05:41:30Z" , MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-17T05:46:11Z","2025-06-17T05:55:03Z" , new GeoPoint(53.86925378333333,10.711828311111113));
        assertVisit(processedVisits.get(2), "2025-06-17T06:00:30Z","2025-06-17T13:10:17Z" , MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-17T13:15:11Z","2025-06-17T13:19:22Z" , new GeoPoint(53.86753033888888,10.710380756666666));
        assertVisit(processedVisits.get(4), "2025-06-17T13:23:48Z","2025-06-17T21:59:44Z" , MOLTKESTR);

        List<Trip> trips = currenTrips();
        assertEquals(4, trips.size());
        assertTrip(trips.get(0), "2025-06-17T05:41:30Z" , MOLTKESTR, "2025-06-17T05:46:11Z" , new GeoPoint(53.86925378333333,10.711828311111113));
        assertTrip(trips.get(1), "2025-06-17T05:55:03Z" , new GeoPoint(53.86925378333333,10.711828311111113), "2025-06-17T06:00:30Z" , MOLTKESTR);
        assertTrip(trips.get(2), "2025-06-17T13:10:17Z" , MOLTKESTR, "2025-06-17T13:15:11Z" , new GeoPoint(53.86753033888888,10.710380756666666));
        assertTrip(trips.get(3), "2025-06-17T13:19:22Z" , new GeoPoint(53.86753033888888,10.710380756666666), "2025-06-17T13:23:48Z" , MOLTKESTR);

        testingService.importAndProcess(user, "/data/gpx/20250618.gpx");

        processedVisits = currentVisits();

        assertEquals(12, processedVisits.size());


        //new visits
        assertVisit(processedVisits.get(0), "2025-06-16T22:01:43Z", "2025-06-17T05:41:30Z", MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-17T05:46:11Z", "2025-06-17T05:55:03Z", new GeoPoint(53.86925378333333,10.711828311111113));
        assertVisit(processedVisits.get(2), "2025-06-17T06:00:30Z", "2025-06-17T13:10:17Z", MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-17T13:15:11Z", "2025-06-17T13:19:22Z", new GeoPoint(53.86753033888888,10.710380756666666));

        //should extend the last visit of the old day
        assertVisit(processedVisits.get(4), "2025-06-17T13:23:48Z" ,"2025-06-18T05:47:44Z", MOLTKESTR);

        //should not touch visits after the new data
        assertVisit(processedVisits.get(5) ,"2025-06-18T05:48:15Z" ,"2025-06-18T05:53:33Z", new GeoPoint(53.863864, 10.708570));
        assertVisit(processedVisits.get(6) ,"2025-06-18T05:56:46Z" ,"2025-06-18T06:03:00Z", new GeoPoint(53.86753033888888,10.710380756666666));
        assertVisit(processedVisits.get(7) ,"2025-06-18T06:07:46Z" ,"2025-06-18T13:02:27Z", MOLTKESTR);
        assertVisit(processedVisits.get(8) ,"2025-06-18T13:07:42Z" ,"2025-06-18T13:16:57Z", new GeoPoint(53.86925378333333,10.711828311111113));
        assertVisit(processedVisits.get(9) ,"2025-06-18T13:20:10Z" ,"2025-06-18T13:29:46Z", new GeoPoint(53.872904, 10.720157));
        assertVisit(processedVisits.get(10),"2025-06-18T13:35:46Z" ,"2025-06-18T15:52:19Z", GARTEN);
        assertVisit(processedVisits.get(11),"2025-06-18T16:05:49Z" ,"2025-06-18T21:59:29Z", MOLTKESTR);
    }

    @Test
    void shouldRecalculateOnIncomingPointsBefore() {
        testingService.importAndProcess(user, "/data/gpx/20250618.gpx");

        List<ProcessedVisit> processedVisits = currentVisits();
        assertEquals(8, processedVisits.size());

        assertVisit(processedVisits.get(0), "2025-06-17T22:01:48Z" ,"2025-06-18T05:47:44Z", MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-18T05:48:15Z" ,"2025-06-18T05:53:33Z", new GeoPoint(53.863864, 10.708570));
        assertVisit(processedVisits.get(2), "2025-06-18T05:56:46Z" ,"2025-06-18T06:03:00Z", ST_THOMAS);
        assertVisit(processedVisits.get(3), "2025-06-18T06:07:46Z" ,"2025-06-18T13:02:27Z", MOLTKESTR);
        assertVisit(processedVisits.get(4), "2025-06-18T13:07:42Z" ,"2025-06-18T13:16:57Z", ST_THOMAS);
        assertVisit(processedVisits.get(5), "2025-06-18T13:20:10Z" ,"2025-06-18T13:29:46Z", new GeoPoint(53.872904, 10.720157));
        assertVisit(processedVisits.get(6), "2025-06-18T13:35:46Z" ,"2025-06-18T15:52:19Z", GARTEN);
        assertVisit(processedVisits.get(7), "2025-06-18T16:05:49Z" ,"2025-06-18T21:59:29Z", MOLTKESTR);

        testingService.importAndProcess(user, "/data/gpx/20250617.gpx");

        processedVisits = currentVisits();

        assertEquals(12, processedVisits.size());

        //new visits
        assertVisit(processedVisits.get(0), "2025-06-16T22:01:43Z", "2025-06-17T05:41:30Z", MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-17T05:46:11Z", "2025-06-17T05:55:03Z", ST_THOMAS);
        assertVisit(processedVisits.get(2), "2025-06-17T06:00:30Z", "2025-06-17T13:10:17Z", MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-17T13:15:11Z", "2025-06-17T13:19:22Z", ST_THOMAS);

        //should extend the last visit of the old day
        assertVisit(processedVisits.get(4), "2025-06-17T13:23:48Z" ,"2025-06-18T05:47:44Z", MOLTKESTR);

        //should not touch visits after the new data
        assertVisit(processedVisits.get(5) ,"2025-06-18T05:48:15Z" ,"2025-06-18T05:53:33Z", new GeoPoint(53.863864, 10.708570));
        assertVisit(processedVisits.get(6) ,"2025-06-18T05:56:46Z" ,"2025-06-18T06:03:00Z", ST_THOMAS);
        assertVisit(processedVisits.get(7) ,"2025-06-18T06:07:46Z" ,"2025-06-18T13:02:27Z", MOLTKESTR);
        assertVisit(processedVisits.get(8) ,"2025-06-18T13:07:42Z" ,"2025-06-18T13:16:57Z", ST_THOMAS);
        assertVisit(processedVisits.get(9) ,"2025-06-18T13:20:10Z" ,"2025-06-18T13:29:46Z", new GeoPoint(53.872904, 10.720157));
        assertVisit(processedVisits.get(10),"2025-06-18T13:35:46Z" ,"2025-06-18T15:52:19Z", GARTEN);
        assertVisit(processedVisits.get(11),"2025-06-18T16:05:49Z" ,"2025-06-18T21:59:29Z", MOLTKESTR);
    }

    @Test
    void shouldCalculateSingleFile() {
        testingService.importAndProcess(user, "/data/gpx/20250618.gpx");

        List<ProcessedVisit> processedVisits = currentVisits();
        assertEquals(8, processedVisits.size());

        assertVisit(processedVisits.get(0), "2025-06-17T22:01:48Z", "2025-06-18T05:47:44Z", MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-18T05:48:15Z", "2025-06-18T05:53:33Z", new GeoPoint(53.86386352818182,10.708570236363638));
        assertVisit(processedVisits.get(2), "2025-06-18T05:56:46Z", "2025-06-18T06:03:00Z", ST_THOMAS);
        assertVisit(processedVisits.get(3), "2025-06-18T06:07:46Z", "2025-06-18T13:02:27Z", MOLTKESTR);
        assertVisit(processedVisits.get(4), "2025-06-18T13:07:42Z", "2025-06-18T13:16:57Z", new GeoPoint(53.868274,10.712731));
        assertVisit(processedVisits.get(5), "2025-06-18T13:20:10Z", "2025-06-18T13:29:46Z", new GeoPoint(53.872904,10.720157));
        assertVisit(processedVisits.get(6), "2025-06-18T13:35:46Z", "2025-06-18T15:52:19Z", GARTEN);
        assertVisit(processedVisits.get(7), "2025-06-18T16:05:49Z", "2025-06-18T21:59:29Z", MOLTKESTR);
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
            assertEquals(processedVisitInOrder.getStartTime().truncatedTo(ChronoUnit.SECONDS), processedVisit.getStartTime().truncatedTo(ChronoUnit.SECONDS));
            assertEquals(processedVisitInOrder.getEndTime().truncatedTo(ChronoUnit.SECONDS), processedVisit.getEndTime().truncatedTo(ChronoUnit.SECONDS));
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

    private static void assertTrip(Trip trip, String startTime, GeoPoint startLocation, String endTime, GeoPoint endLocation) {
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
