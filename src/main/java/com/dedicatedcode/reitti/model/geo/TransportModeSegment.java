package com.dedicatedcode.reitti.model.geo;

public record TransportModeSegment(
        TransportMode mode,
        long offsetSeconds,
        long durationSeconds,
        double distanceMeters
) {
}
