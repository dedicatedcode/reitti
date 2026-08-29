package com.dedicatedcode.reitti.service.processing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class GeoPointAnomalyFilterConfig {
    private final double maxSpeedKmh;
    private final double maxAccuracyMeters;
    private final int windowSize;

    public GeoPointAnomalyFilterConfig(
            @Value("${reitti.geo-point-filter.max-speed-kmh:1000}") double maxSpeedKmh,
            @Value("${reitti.geo-point-filter.max-accuracy-meters:100}") double maxAccuracyMeters,
            @Value("${reitti.geo-point-filter.window-size:5}") int windowSize) {
        this.maxSpeedKmh = maxSpeedKmh;
        this.maxAccuracyMeters = maxAccuracyMeters;
        this.windowSize = windowSize;
    }

    public double getMaxSpeedKmh() {
        return maxSpeedKmh;
    }

    public double getMaxAccuracyMeters() {
        return maxAccuracyMeters;
    }

    public int getWindowSize() {
        return windowSize;
    }
}
