package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.geo.TransportModeConfig;
import org.springframework.stereotype.Component;

@Component
public class ModeEmissionModel {

    private static final double EPSILON = 1e-6;
    private static final double MIN_SIGMA_KMH = 2.0;
    private static final double STOP_SPEED_KMH = 2.0;
    private static final double P85_WEIGHT = 0.5;

    /**
     * A mode is most likely around the middle of its configured range and becomes
     * steadily less likely towards and beyond the edges. Stops are uninformative:
     * they must not pull the classifier towards the slowest mode.
     */
    public double logMembership(ChunkFeatures features, TransportModeConfig config) {
        if (!features.reliable() || features.speedKmh() < STOP_SPEED_KMH) {
            return 0.0;
        }
        return logMembership(features.speedKmh(), config);
    }

    public double logLikelihood(ChunkFeatures features, TransportModeConfig config) {
        if (!features.reliable() || features.speedKmh() < STOP_SPEED_KMH) {
            return 0.0;
        }
        // The median describes the steady part of the window, the p85 the peak. A car
        // pulling away from a stop has a much higher peak than a bicycle at the same median.
        double median = logMembership(features.speedKmh(), config);
        double peak = logMembership(features.p85SpeedKmh(), config);
        return median + P85_WEIGHT * peak + Math.log(Math.max(0.01, config.preferenceWeight()));
    }

    private double logMembership(double speed, TransportModeConfig config) {
        double mean = (config.minKmh() + config.maxKmh()) / 2.0;
        double sigma = Math.max(MIN_SIGMA_KMH, (config.maxKmh() - config.minKmh()) / 6.0);
        double z = (speed - mean) / sigma;
        return Math.log(Math.exp(-0.5 * z * z) + EPSILON);
    }
}
