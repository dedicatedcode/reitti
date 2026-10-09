package com.dedicatedcode.reitti.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocationDensityConfig {
    
    @Value("${reitti.location.density.target-points-per-minute:4}")
    private int targetPointsPerMinute;

    @Value("${reitti.location.density.max-stationary-speed-kmh:1.0}")
    private double maxStationarySpeedKmh;

    @Value("${reitti.location.density.max-interpolation-gap-hours:12}")
    private int maxInterpolationGapHours;

    public int getTargetPointsPerMinute() {
        return targetPointsPerMinute;
    }

    public double getMaxStationarySpeedKmh() {
        return maxStationarySpeedKmh;
    }

    public double getMaxStationarySpeedMps() {
        return maxStationarySpeedKmh / 3.6;
    }

    public int getMaxInterpolationGapHours() {
        return maxInterpolationGapHours;
    }
    
    public int getTargetIntervalSeconds() {
        return 60 / targetPointsPerMinute;
    }
    
    public int getToleranceSeconds() {
        return getTargetIntervalSeconds() / 2;
    }
    
    public int getGapThresholdSeconds() {
        return getTargetIntervalSeconds() * 2;
    }
}
