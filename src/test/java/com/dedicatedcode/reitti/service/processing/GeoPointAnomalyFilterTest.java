package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.TestUtils;
import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.geo.GeoUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class GeoPointAnomalyFilterTest {

    @Test
    void shouldFilterOutOdense() {
        List<LocationPoint> points = TestUtils.readFromTableOutput("/data/table/2013-06-03.table");
        GeoPointAnomalyFilter filter = new GeoPointAnomalyFilter(new GeoPointAnomalyFilterConfig(1000, 100, 200));

        List<LocationPoint> result = filter.filterAnomalies(points);

        LocationPoint invalidPoint1 = new LocationPoint();
        invalidPoint1.setLatitude(10.3483634);
        invalidPoint1.setLongitude(10.3485441);
        LocationPoint invalidPoint2 = new LocationPoint();
        invalidPoint2.setLatitude(10.3483634);
        invalidPoint2.setLongitude(55.3998631);

        for (LocationPoint locationPoint : result) {
            assertFalse(GeoUtils.distanceInMeters(invalidPoint1, locationPoint) < 100);
            assertFalse(GeoUtils.distanceInMeters(invalidPoint2, locationPoint) < 100);
        }
    }
}