package com.dedicatedcode.reitti.model.geo;

public record TransportModeSegment(
        TransportMode mode,
        long offsetSeconds,
        long durationSeconds,
        double distanceMeters,
        Double confidence
) {

    public TransportModeSegment {
        if (confidence == null) {
            confidence = 1.0;
        }
    }

    public TransportModeSegment(TransportMode mode, long offsetSeconds, long durationSeconds, double distanceMeters) {
        this(mode, offsetSeconds, durationSeconds, distanceMeters, 1.0);
    }
}
