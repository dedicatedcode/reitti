package com.dedicatedcode.reitti.model.geo;

import java.io.Serializable;

public record TransportModeConfig(TransportMode mode, Double maxKmh) implements Serializable {

}
