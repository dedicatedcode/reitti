package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.geo.SourceLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
@TestPropertySource(properties = "reitti.processing.point-chunk-size=2")
class AnomalyProcessingServiceTest {

    @Autowired
    private SourceLocationPointJdbcService rawLocationPointJdbcService;

    @Autowired
    private AnomalyProcessingService anomalyProcessingService;
    @Autowired
    private TestingService testingService;

    @Test
    void shouldFilterOutSinglePoint() {
        User user = testingService.randomUser();

        List<LocationPoint> points = new ArrayList<>();
        points.add(createLocationPoint("2022-01-01T09:03:03Z", 53.55150, 9.96642, 0.0));
        points.add(createLocationPoint("2022-01-01T09:05:03Z", 53.55097, 9.97015, 10.0));
        points.add(createLocationPoint("2022-01-01T09:08:42Z", 53.55111, 9.97009, 10.0));
        points.add(createLocationPoint("2022-01-01T09:09:15Z", 53.55680, 9.96866, 10.0));
        points.add(createLocationPoint("2022-01-01T09:10:03Z", 53.55671, 9.97352, 10.0));
        points.add(createLocationPoint("2022-01-01T09:10:10Z", 53.55772, 9.96886, 10.0));
        points.add(createLocationPoint("2022-01-01T09:10:41Z", 53.55772, 9.96886, 10.0));
        points.add(createLocationPoint("2022-01-01T09:10:59Z", 53.56480, 9.96921, 10.0));
        points.add(createLocationPoint("2022-01-01T09:17:11Z", 53.56428, 9.97763, 10.0));
        points.add(createLocationPoint("2022-01-01T09:17:25Z", 53.56413, 9.98185, 10.0));
        points.add(createLocationPoint("2022-01-01T09:17:26Z", 53.56413, 9.98185, 10.0));
        points.add(createLocationPoint("2022-01-01T09:17:41Z", 53.56370, 9.98788, 10.0));
        points.add(createLocationPoint("2022-01-01T09:17:59Z", 53.56142, 9.98905, 10.0));
        points.add(createLocationPoint("2022-01-01T09:26:24Z", 51.68968, 5.28917, 10.0)); //shall be filtered out

        assertEquals(14, this.rawLocationPointJdbcService.bulkInsert(user, null, points));

        // the caller passes the range to process - including enough context for the boundary
        this.anomalyProcessingService.processAndMarkAnomalies(user, null, Instant.parse("2022-01-01T09:03:03Z"), Instant.parse("2022-01-01T09:26:25Z"));

        List<SourceLocationPoint> storedPoints = this.rawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(user, null, Instant.parse("2022-01-01T09:03:03Z"), Instant.parse("2022-01-01T09:27:24Z"), true, true);
        assertEquals(14, storedPoints.size(), "Point should be stored");
        List<SourceLocationPoint> filteredPoints = storedPoints.stream().filter(SourceLocationPoint::isInvalid).toList();
        assertEquals(1, filteredPoints.size(), "One point should be marked as invalid");
        assertEquals("2022-01-01T09:26:24Z", filteredPoints.getFirst().getTimestamp().toString());
    }

    @Test
    void shouldFilterOutSinglePointButRetainOne() {
        User user = testingService.randomUser();

        List<LocationPoint> points = new ArrayList<>();
        points.add(createLocationPoint("2022-01-01T09:03:03Z", 53.55150, 9.96642, 0.0));
        points.add(createLocationPoint("2022-01-01T09:05:03Z", 53.55097, 9.97015, 10.0));
        points.add(createLocationPoint("2022-01-01T09:08:42Z", 53.55111, 9.97009, 10.0));
        points.add(createLocationPoint("2022-01-01T09:09:15Z", 53.55680, 9.96866, 10.0));
        points.add(createLocationPoint("2022-01-01T09:10:03Z", 53.55671, 9.97352, 10.0));
        points.add(createLocationPoint("2022-01-01T09:10:10Z", 53.55772, 9.96886, 10.0));
        points.add(createLocationPoint("2022-01-01T09:10:41Z", 53.55772, 9.96886, 10.0));
        points.add(createLocationPoint("2022-01-01T09:10:59Z", 53.56480, 9.96921, 10.0));
        points.add(createLocationPoint("2022-01-01T09:17:11Z", 53.56428, 9.97763, 10.0));
        points.add(createLocationPoint("2022-01-01T09:17:25Z", 53.56413, 9.98185, 10.0));
        points.add(createLocationPoint("2022-01-01T09:17:26Z", 53.56413, 9.98185, 10.0));
        points.add(createLocationPoint("2022-01-01T09:17:41Z", 53.56370, 9.98788, 10.0));
        points.add(createLocationPoint("2022-01-01T09:17:59Z", 53.56142, 9.98905, 10.0));
        points.add(createLocationPoint("2022-01-01T09:26:24Z", 51.68968, 5.28917, 10.0)); //shall be filtered out
        points.add(createLocationPoint("2022-01-01T09:26:48Z", 53.56212, 9.98635, 10.0));

        assertEquals(15, this.rawLocationPointJdbcService.bulkInsert(user, null, points));

        this.anomalyProcessingService.processAndMarkAnomalies(user, null, Instant.parse("2022-01-01T09:03:03Z"), Instant.parse("2022-01-01T09:26:49Z"));

        List<SourceLocationPoint> storedPoints = this.rawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(user, null, Instant.parse("2022-01-01T09:03:03Z"), Instant.parse("2022-01-01T09:27:24Z"), true, true);
        assertEquals(15, storedPoints.size(), "Point should be stored");
        List<SourceLocationPoint> filteredPoints = storedPoints.stream().filter(SourceLocationPoint::isInvalid).toList();
        assertEquals(1, filteredPoints.size(), "One point should be marked as invalid");
        assertEquals("2022-01-01T09:26:24Z", filteredPoints.getFirst().getTimestamp().toString());
    }

    @Test
    void shouldFilter() {
        User user = testingService.randomUser();

        //store a point which is totally off
        List<LocationPoint> points = List.of(createLocationPoint("2022-01-01T09:02:24Z", 51.68968, 5.28917, 10.0));
        assertEquals(1, this.rawLocationPointJdbcService.bulkInsert(user, null, points));
        this.anomalyProcessingService.processAndMarkAnomalies(user, null, Instant.parse("2022-01-01T09:00:00Z"), Instant.parse("2022-01-01T09:26:24Z")); //shall not mark our single point as invalid

        List<SourceLocationPoint> storedPoints = this.rawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(user, null, Instant.parse("2022-01-01T09:00:00Z"), Instant.parse("2022-01-01T09:27:24Z"), true, true);
        assertTrue(storedPoints.stream().noneMatch(SourceLocationPoint::isInvalid), "Point should not be marked as invalid");

        points = new ArrayList<>();
        points.add(createLocationPoint("2022-01-01T09:03:03Z", 53.55150, 9.96642, 0.0));
        points.add(createLocationPoint("2022-01-01T09:05:03Z", 53.55097, 9.97015, 10.0));
        points.add(createLocationPoint("2022-01-01T09:08:42Z", 53.55111, 9.97009, 10.0));
        points.add(createLocationPoint("2022-01-01T09:09:15Z", 53.55680, 9.96866, 10.0));
        points.add(createLocationPoint("2022-01-01T09:10:03Z", 53.55671, 9.97352, 10.0));
        points.add(createLocationPoint("2022-01-01T09:10:10Z", 53.55772, 9.96886, 10.0));
        points.add(createLocationPoint("2022-01-01T09:10:41Z", 53.55772, 9.96886, 10.0));
        points.add(createLocationPoint("2022-01-01T09:10:59Z", 53.56480, 9.96921, 10.0));
        points.add(createLocationPoint("2022-01-01T09:17:11Z", 53.56428, 9.97763, 10.0));
        points.add(createLocationPoint("2022-01-01T09:17:25Z", 53.56413, 9.98185, 10.0));
        points.add(createLocationPoint("2022-01-01T09:17:26Z", 53.56413, 9.98185, 10.0));
        points.add(createLocationPoint("2022-01-01T09:17:41Z", 53.56370, 9.98788, 10.0));
        points.add(createLocationPoint("2022-01-01T09:17:59Z", 53.56142, 9.98905, 10.0));

        //now store new points
        this.rawLocationPointJdbcService.bulkInsert(user, null, points);
        this.anomalyProcessingService.processAndMarkAnomalies(user, null, Instant.parse("2022-01-01T09:02:24Z"), Instant.parse("2022-01-01T09:17:59Z"));

        storedPoints = this.rawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(user, null, Instant.parse("2022-01-01T09:00:00Z"), Instant.parse("2022-01-01T09:27:24Z"), true, true);

        List<SourceLocationPoint> storedInvalidPoints = storedPoints.stream().filter(SourceLocationPoint::isInvalid).toList();
        assertEquals(1, storedInvalidPoints.size(), "One point should be marked as invalid");
        assertEquals("2022-01-01T09:02:24Z", storedInvalidPoints.getFirst().getTimestamp().toString());
    }

    @Test
    void shouldFilterSinglePointByAccuracy() {
        User user = testingService.randomUser();
        LocationPoint point = createLocationPoint("2022-01-01T09:00:00Z", 53.55150, 9.96642, 101.0);
        rawLocationPointJdbcService.bulkInsert(user, null, List.of(point));

        anomalyProcessingService.processAndMarkAnomalies(user, null, Instant.parse("2022-01-01T09:00:00Z"), Instant.parse("2022-01-01T09:00:01Z"));

        List<SourceLocationPoint> storedPoints = rawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(
                user, null, Instant.parse("2022-01-01T09:00:00Z"), Instant.parse("2022-01-01T09:00:01Z"), true, true);
        assertEquals(1, storedPoints.size());
        assertTrue(storedPoints.getFirst().isInvalid());
    }

    private LocationPoint createLocationPoint(String timestamp, double latitude, double longitude, double accuracy) {
        LocationPoint locationPoint = new LocationPoint();
        locationPoint.setTimestamp(Instant.parse(timestamp));
        locationPoint.setLatitude(latitude);
        locationPoint.setLongitude(longitude);
        locationPoint.setAccuracyMeters(accuracy);
        return locationPoint;
    }
}