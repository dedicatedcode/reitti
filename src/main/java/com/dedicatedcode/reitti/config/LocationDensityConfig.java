package com.dedicatedcode.reitti.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocationDensityConfig {
    
    @Value("${reitti.location.density.target-points-per-minute:4}")
    private int targetPointsPerMinute;
    
    public int getTargetPointsPerMinute() {
        return targetPointsPerMinute;
    }
    
    /**
     * Calculate the target interval in seconds between points
     * @return seconds between points (e.g., 15 seconds for 4 points per minute)
     */
    public int getTargetIntervalSeconds() {
        return 60 / targetPointsPerMinute;
    }
    
    /**
     * Calculate the tolerance window in seconds (half the target interval)
     * @return tolerance in seconds
     */
    public int getToleranceSeconds() {
        return getTargetIntervalSeconds() / 2;
    }
    
    /**
     * Calculate the gap threshold - gaps larger than this need synthetic points
     * @return gap threshold in seconds (2x target interval)
     */
    public int getGapThresholdSeconds() {
        return getTargetIntervalSeconds() * 2;
    }
}
