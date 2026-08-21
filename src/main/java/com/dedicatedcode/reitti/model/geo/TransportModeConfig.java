package com.dedicatedcode.reitti.model.geo;

import java.io.Serializable;

public record TransportModeConfig(TransportMode mode, Double maxKmh, String color, String icon) implements Serializable {

    public TransportModeConfig(TransportMode mode, Double maxKmh) {
        this(mode, maxKmh, null, null);
    }
}
