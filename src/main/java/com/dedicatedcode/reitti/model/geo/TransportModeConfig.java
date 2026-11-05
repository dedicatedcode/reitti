package com.dedicatedcode.reitti.model.geo;

import java.io.Serializable;

public record TransportModeConfig(
        TransportMode mode,
        double minKmh,
        double maxKmh,
        double preferenceWeight,
        boolean enabled,
        String color,
        String icon
) implements Serializable {

    public TransportModeConfig(TransportMode mode, double minKmh, double maxKmh) {
        this(mode, minKmh, maxKmh, 1.0, true, null, null);
    }

    public TransportModeConfig(TransportMode mode, double minKmh, double maxKmh, String color, String icon) {
        this(mode, minKmh, maxKmh, 1.0, true, color, icon);
    }
}
