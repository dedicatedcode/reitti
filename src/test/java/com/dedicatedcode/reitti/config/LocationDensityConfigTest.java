package com.dedicatedcode.reitti.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "reitti.location.density.target-points-per-minute=4"
})
class LocationDensityConfigTest {

    @Autowired
    private LocationDensityConfig config;

    @Test
    void shouldLoadTargetPointsPerMinuteFromProperties() {
        assertEquals(4, config.getTargetPointsPerMinute());
    }

    @Test
    void shouldCalculateCorrectTargetInterval() {
        // For 4 points per minute: 60 / 4 = 15 seconds
        assertEquals(15, config.getTargetIntervalSeconds());
    }

    @Test
    void shouldCalculateCorrectTolerance() {
        // Tolerance should be half the target interval: 15 / 2 = 7.5 seconds
        assertEquals(7, config.getToleranceSeconds()); // Integer division: 15/2 = 7
    }

    @Test
    void shouldCalculateCorrectGapThreshold() {
        // Gap threshold should be 2x target interval: 15 * 2 = 30 seconds
        assertEquals(30, config.getGapThresholdSeconds());
    }
}

@SpringBootTest
@TestPropertySource(properties = {
    "reitti.location.density.target-points-per-minute=6"
})
class LocationDensityConfigCustomValueTest {

    @Autowired
    private LocationDensityConfig config;

    @Test
    void shouldHandleCustomTargetPointsPerMinute() {
        assertEquals(6, config.getTargetPointsPerMinute());
        assertEquals(10, config.getTargetIntervalSeconds()); // 60 / 6 = 10
        assertEquals(5, config.getToleranceSeconds()); // 10 / 2 = 5
        assertEquals(20, config.getGapThresholdSeconds()); // 10 * 2 = 20
    }
}
