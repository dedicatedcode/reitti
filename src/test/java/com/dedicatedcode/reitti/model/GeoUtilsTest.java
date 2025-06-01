package com.dedicatedcode.reitti.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeoUtilsTest {

    @Test
    void shouldCalculateCorrectDistance() {
        double distanceInMeters = GeoUtils.distanceInMeters(53.86465327678005, 10.69802287418616, 53.86617166627736, 10.701209338353138);
        assertEquals(268.62, distanceInMeters, 0.01);
    }
}