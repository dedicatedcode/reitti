package com.dedicatedcode.reitti.service.processing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GeoPointAnomalyFilterConfig {
    public final double maxSpeedKmh;           // Maximum reasonable speed
    public final double maxAccuracyMeters;     // Maximum acceptable accuracy
    public final double maxDistanceJumpMeters; // Maximum jump between points
    public final double edgeToleranceMultiplier = 1.5;   // Extra tolerance for edge points

    public GeoPointAnomalyFilterConfig(
            @Value("${reitti.geo-point-filter.max-speed-kmh:1000}") double maxSpeedKmh,
            @Value("${reitti.geo-point-filter.max-accuracy-meters:100}") double maxAccuracyMeters,
            @Value("${reitti.geo-point-filter.max-distance-jump-meters:5000}") double maxDistanceJumpMeters) {
        this.maxSpeedKmh = maxSpeedKmh;
        this.maxAccuracyMeters = maxAccuracyMeters;
        this.maxDistanceJumpMeters = maxDistanceJumpMeters;
    }
}
