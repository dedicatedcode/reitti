package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.TestUtils;
import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.model.GeoUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class GeoPointAnomalyFilterTest {

    @Test
    void shouldFilterOutOdense() {
        List<LocationDataRequest.LocationPoint> points = TestUtils.readFromTableOutput("/data/table/2013-06-03.table");
        GeoPointAnomalyFilter filter = new GeoPointAnomalyFilter(new GeoPointAnomalyFilterConfig(1000, 100, 200));

        List<LocationDataRequest.LocationPoint> result = filter.filterAnomalies(points);

        LocationDataRequest.LocationPoint invalidPoint1 = new LocationDataRequest.LocationPoint();
        invalidPoint1.setLatitude(10.3483634);
        invalidPoint1.setLongitude(10.3485441);
        LocationDataRequest.LocationPoint invalidPoint2 = new LocationDataRequest.LocationPoint();
        invalidPoint2.setLatitude(10.3483634);
        invalidPoint2.setLongitude(55.3998631);

        for (LocationDataRequest.LocationPoint locationPoint : result) {
            assertFalse(GeoUtils.distanceInMeters(invalidPoint1, locationPoint) < 100);
            assertFalse(GeoUtils.distanceInMeters(invalidPoint2, locationPoint) < 100);
        }
    }
}