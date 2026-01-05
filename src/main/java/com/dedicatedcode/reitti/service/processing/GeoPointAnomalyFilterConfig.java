package com.dedicatedcode.reitti.service.processing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class GeoPointAnomalyFilterConfig {
    private final double maxSpeedKmh;
    private final double maxAccuracyMeters;
    private final double maxDistanceJumpMeters;
    private final double edgeToleranceMultiplier = 1.5;
    private final Duration historyLookback;
    private final int windowSize;

    public GeoPointAnomalyFilterConfig(
            @Value("${reitti.geo-point-filter.max-speed-kmh:1000}") double maxSpeedKmh,
            @Value("${reitti.geo-point-filter.max-accuracy-meters:100}") double maxAccuracyMeters,
            @Value("${reitti.geo-point-filter.max-distance-jump-meters:5000}") double maxDistanceJumpMeters,
            @Value("${reitti.geo-point-filter.history-lookback-hours:2}") int historyLookback,
            @Value("${reitti.geo-point-filter.window-size:5}") int windowSize) {
        this.maxSpeedKmh = maxSpeedKmh;
        this.maxAccuracyMeters = maxAccuracyMeters;
        this.maxDistanceJumpMeters = maxDistanceJumpMeters;
        this.historyLookback = Duration.ofHours(historyLookback);
        this.windowSize = windowSize;
    }

    public double getMaxSpeedKmh() {
        return maxSpeedKmh;
    }

    public double getMaxAccuracyMeters() {
        return maxAccuracyMeters;
    }

    public double getMaxDistanceJumpMeters() {
        return maxDistanceJumpMeters;
    }

    public double getEdgeToleranceMultiplier() {
        return edgeToleranceMultiplier;
    }

    public Duration getHistoryLookback() {
        return historyLookback;
    }

    public int getWindowSize() {
        return windowSize;
    }
}
