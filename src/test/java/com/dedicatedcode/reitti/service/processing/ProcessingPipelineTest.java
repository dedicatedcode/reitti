package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestConstants;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.GeoPoint;
import com.dedicatedcode.reitti.model.ProcessedVisit;
import com.dedicatedcode.reitti.model.Visit;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.VisitJdbcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
public class ProcessingPipelineTest {

    @Autowired
    private TestingService testingService;

    @Autowired
    private ProcessedVisitJdbcService processedVisitJdbcService;

    @Autowired
    private VisitJdbcService visitJdbcService;

    @BeforeEach
    public void setUp() {
        this.testingService.clearData();
    }

    @Test
    void shouldRecalculateOnIncomingPointsAfter() {
        testingService.importAndProcess("/data/gpx/20250617.gpx");

        List<ProcessedVisit> processedVisits = currentVisits();
        assertEquals(5, processedVisits.size());

        this.visitJdbcService.findByUser(this.testingService.admin()).stream().sorted(Comparator.comparing(Visit::getStartTime)).forEach(System.out::println);

        assertVisit(processedVisits.get(0), "2025-06-16T22:00:09.154Z", "2025-06-17T05:39:50.330Z", TestConstants.Points.MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-17T05:44:39.578Z", "2025-06-17T05:54:32.974Z", TestConstants.Points.ST_THOMAS);
        assertVisit(processedVisits.get(2), "2025-06-17T05:58:10.797Z", "2025-06-17T13:08:53.346Z", TestConstants.Points.MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-17T13:12:33.214Z", "2025-06-17T13:18:20.778Z", TestConstants.Points.ST_THOMAS);
        assertVisit(processedVisits.get(4), "2025-06-17T13:22:00.725Z", "2025-06-17T21:59:44.876Z", TestConstants.Points.MOLTKESTR);

        testingService.importAndProcess("/data/gpx/20250618.gpx");

        processedVisits = currentVisits();

        assertEquals(10, processedVisits.size());

        //should not touch visits before the new data
        assertVisit(processedVisits.get(0), "2025-06-16T22:00:09.154Z", "2025-06-17T05:39:50.330Z", TestConstants.Points.MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-17T05:44:39.578Z", "2025-06-17T05:54:32.974Z", TestConstants.Points.ST_THOMAS);
        assertVisit(processedVisits.get(2), "2025-06-17T05:58:10.797Z", "2025-06-17T13:08:53.346Z", TestConstants.Points.MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-17T13:12:33.214Z", "2025-06-17T13:18:20.778Z", TestConstants.Points.ST_THOMAS);

        //should extend the last visit of the old day
        assertVisit(processedVisits.get(4), "2025-06-17T13:22:00.725Z", "2025-06-18T05:45:00.682Z", TestConstants.Points.MOLTKESTR);

        //new visits
        assertVisit(processedVisits.get(5), "2025-06-18T05:55:09.648Z","2025-06-18T06:02:05.400Z", TestConstants.Points.ST_THOMAS);
        assertVisit(processedVisits.get(6), "2025-06-18T06:06:43.274Z","2025-06-18T13:01:23.419Z", TestConstants.Points.MOLTKESTR);
        assertVisit(processedVisits.get(7), "2025-06-18T13:05:04.278Z","2025-06-18T13:13:16.416Z", TestConstants.Points.ST_THOMAS);
        assertVisit(processedVisits.get(8), "2025-06-18T13:34:07Z","2025-06-18T15:50:40Z", TestConstants.Points.GARTEN);
        assertVisit(processedVisits.get(9), "2025-06-18T16:05:49.301Z","2025-06-18T21:59:29.055Z", TestConstants.Points.MOLTKESTR);
    }

    @Test
    void shouldRecalculateOnIncomingPointsBefore() {
        testingService.importAndProcess("/data/gpx/20250618.gpx");

        List<ProcessedVisit> processedVisits = currentVisits();
        assertEquals(6, processedVisits.size());
        assertVisit(processedVisits.get(0), "2025-06-17T22:00:15.843Z", "2025-06-18T05:45:00.682Z", TestConstants.Points.MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-18T05:55:09.648Z","2025-06-18T06:02:05.400Z", TestConstants.Points.ST_THOMAS);
        assertVisit(processedVisits.get(2), "2025-06-18T06:06:43.274Z","2025-06-18T13:01:23.419Z", TestConstants.Points.MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-18T13:05:04.278Z","2025-06-18T13:13:16.416Z", TestConstants.Points.ST_THOMAS);
        assertVisit(processedVisits.get(4), "2025-06-18T13:34:07Z","2025-06-18T15:50:40Z", TestConstants.Points.GARTEN);
        assertVisit(processedVisits.get(5), "2025-06-18T16:05:49.301Z","2025-06-18T21:59:29.055Z", TestConstants.Points.MOLTKESTR);

        testingService.importAndProcess("/data/gpx/20250617.gpx");
        this.visitJdbcService.findByUser(this.testingService.admin()).stream().sorted(Comparator.comparing(Visit::getStartTime)).forEach(System.out::println);

        processedVisits = currentVisits();

        assertEquals(10, processedVisits.size());

        //should not touch visits before the new data
        assertVisit(processedVisits.get(0), "2025-06-16T22:00:09.154Z", "2025-06-17T05:39:50.330Z", TestConstants.Points.MOLTKESTR);
        assertVisit(processedVisits.get(1), "2025-06-17T05:44:39.578Z", "2025-06-17T05:54:32.974Z", TestConstants.Points.ST_THOMAS);
        assertVisit(processedVisits.get(2), "2025-06-17T05:58:10.797Z", "2025-06-17T13:08:53.346Z", TestConstants.Points.MOLTKESTR);
        assertVisit(processedVisits.get(3), "2025-06-17T13:12:33.214Z", "2025-06-17T13:18:20.778Z", TestConstants.Points.ST_THOMAS);

        //should extend the first visit of the old day
        assertVisit(processedVisits.get(4), "2025-06-17T13:21:28.334Z", "2025-06-18T05:45:00.682Z", TestConstants.Points.MOLTKESTR);

        //new visits
        assertVisit(processedVisits.get(5), "2025-06-18T05:55:09.648Z","2025-06-18T06:02:05.400Z", TestConstants.Points.ST_THOMAS);
        assertVisit(processedVisits.get(6), "2025-06-18T06:06:43.274Z","2025-06-18T13:01:23.419Z", TestConstants.Points.MOLTKESTR);
        assertVisit(processedVisits.get(7), "2025-06-18T13:05:04.278Z","2025-06-18T13:13:16.416Z", TestConstants.Points.ST_THOMAS);
        assertVisit(processedVisits.get(8), "2025-06-18T13:34:07Z","2025-06-18T15:50:40Z", TestConstants.Points.GARTEN);
        assertVisit(processedVisits.get(9), "2025-06-18T16:05:49.301Z","2025-06-18T21:59:29.055Z", TestConstants.Points.MOLTKESTR);
    }

    private static void assertVisit(ProcessedVisit processedVisit, String startTime, String endTime, GeoPoint location) {
        assertEquals(Instant.parse(startTime), processedVisit.getStartTime());
        assertEquals(Instant.parse(endTime), processedVisit.getEndTime());
        GeoPoint currentLocation = GeoPoint.from(processedVisit.getPlace().getGeom());
        assertTrue(location.near(currentLocation), "Locations are not near to each other. \nExpected [" + currentLocation + "] to be in range \nto [" + location + "]");
    }

    private List<ProcessedVisit> currentVisits() {
        return this.processedVisitJdbcService.findByUser(testingService.admin());
    }
}
