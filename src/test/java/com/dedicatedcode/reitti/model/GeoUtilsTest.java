package com.dedicatedcode.reitti.model;

import com.dedicatedcode.reitti.TestUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeoUtilsTest {

    @Test
    void shouldCalculateCorrectDistance() {
        double distanceInMeters = GeoUtils.distanceInMeters(53.86465327678005, 10.69802287418616, 53.86617166627736, 10.701209338353138);
        assertEquals(268.62, distanceInMeters, 0.01);
    }
    
    @Test
    void shouldConvertMetersToDegreesCorrectly() {
        // Test at the equator (0 degrees latitude)
        double[] degreesAtEquator = GeoUtils.metersToDegreesAtPosition(111320, 0);
        assertEquals(1.0, degreesAtEquator[0], 0.001); // 1 degree latitude
        assertEquals(1.0, degreesAtEquator[1], 0.001); // 1 degree longitude at equator
        
        // Test at 60 degrees north
        double[] degreesAt60North = GeoUtils.metersToDegreesAtPosition(111320, 60);
        assertEquals(1.0, degreesAt60North[0], 0.001); // 1 degree latitude
        assertEquals(2.0, degreesAt60North[1], 0.001); // 2 degrees longitude at 60°N (approximately)
        
        // Test with a smaller distance
        double[] degreesFor100m = GeoUtils.metersToDegreesAtPosition(100, 45);
        assertEquals(0.0009, degreesFor100m[0], 0.0001); // About 0.0009 degrees latitude
        assertEquals(0.00127, degreesFor100m[1], 0.0001); // About 0.00127 degrees longitude at 45°N
    }

    @Test
    void shouldCalculateCorrectTripDistances() {
        List<RawLocationPoint> points = TestUtils.loadFromCsv("/data/raw/trip_1.csv");
        double calculatedDistances = GeoUtils.calculateTripDistance(points);

        assertEquals(22664.67856, calculatedDistances, 0.01);
    }
}
