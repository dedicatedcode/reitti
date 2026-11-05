package com.dedicatedcode.reitti.service.processing;

public record ChunkFeatures(
        long startOffsetSeconds,
        long endOffsetSeconds,
        double speedKmh,
        double p85SpeedKmh,
        double stopFraction,
        double sinuosity,
        double speedVariance,
        double meanAccuracyMeters,
        double distanceMeters,
        int pointCount
) {

    public long durationSeconds() {
        return Math.max(1, endOffsetSeconds - startOffsetSeconds);
    }

    public boolean reliable() {
        return pointCount >= 2;
    }
}
