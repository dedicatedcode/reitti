package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestUtils;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.GeoUtils;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@IntegrationTest
class GeoPointAnomalyFilterTest {

    @Autowired
    private RawLocationPointJdbcService rawLocationPointJdbcService;

    @Autowired
    private GeoPointAnomalyFilter anomalyFilter;

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

        assertEquals(13, this.rawLocationPointJdbcService.bulkInsert(user, points));

        LocationPoint toBeFilteredOut = createLocationPoint("2022-01-01T09:26:24Z", 51.68968, 5.28917, 10.0);
        List<LocationPoint> result = this.anomalyFilter.filterAnomalies(user, List.of(toBeFilteredOut));

        assertTrue(result.isEmpty(), "Point should be filtered out");
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

        assertEquals(13, this.rawLocationPointJdbcService.bulkInsert(user, points));

        List<LocationPoint> result = this.anomalyFilter.filterAnomalies(user, List.of(
                createLocationPoint("2022-01-01T09:26:24Z", 51.68968, 5.28917, 10.0), //shall be filtered out
                createLocationPoint("2022-01-01T09:26:48Z", 53.56212, 9.98635, 10.0)
        ));

        assertEquals(1, result.size());
        assertEquals("2022-01-01T09:26:48Z", result.getFirst().getTimestamp());
    }

    @Test
    void shouldFilter() {
        User user = testingService.randomUser();

        //store a point which is totally off
        assertEquals(1, this.rawLocationPointJdbcService.bulkInsert(user, List.of(createLocationPoint("2022-01-01T09:02:24Z", 51.68968, 5.28917, 10.0))));

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

        List<LocationPoint> result = this.anomalyFilter.filterAnomalies(user, points);

        System.out.println();
    }

    private LocationPoint createLocationPoint(String timestamp, double latitude, double longitude, double accuracy) {
        LocationPoint locationPoint = new LocationPoint();
        locationPoint.setTimestamp(timestamp);
        locationPoint.setLatitude(latitude);
        locationPoint.setLongitude(longitude);
        locationPoint.setAccuracyMeters(accuracy);
        return locationPoint;
    }
}