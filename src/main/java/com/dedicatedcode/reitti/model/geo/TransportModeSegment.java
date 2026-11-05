package com.dedicatedcode.reitti.model.geo;

public record TransportModeSegment(
        TransportMode mode,
        long offsetSeconds,
        long durationSeconds,
        double distanceMeters,
        double confidence
) {

    public TransportModeSegment(TransportMode mode, long offsetSeconds, long durationSeconds, double distanceMeters) {
        this(mode, offsetSeconds, durationSeconds, distanceMeters, 1.0);
    }
}
