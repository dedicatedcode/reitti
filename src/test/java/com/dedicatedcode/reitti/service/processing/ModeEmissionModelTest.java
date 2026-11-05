package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.geo.TransportModeConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModeEmissionModelTest {

    private final ModeEmissionModel model = new ModeEmissionModel();
    private final TransportModeConfig cycling = new TransportModeConfig(TransportMode.CYCLING, 5.0, 25.0);
    private final TransportModeConfig driving = new TransportModeConfig(TransportMode.DRIVING, 20.0, 130.0);

    @Test
    void peakSpeedFavoursDrivingForAcceleratingCar() {
        ChunkFeatures accelerating = new ChunkFeatures(0, 60, 18.6, 36.5, 0.4, 1.03, 192, 10, 500, 6);

        assertThat(model.logLikelihood(accelerating, driving))
                .isGreaterThan(model.logLikelihood(accelerating, cycling));
    }

    @Test
    void steadyCyclingStaysCycling() {
        ChunkFeatures steady = new ChunkFeatures(0, 60, 20.0, 22.0, 0.0, 1.0, 5, 10, 300, 6);

        assertThat(model.logLikelihood(steady, cycling))
                .isGreaterThan(model.logLikelihood(steady, driving));
    }
}
