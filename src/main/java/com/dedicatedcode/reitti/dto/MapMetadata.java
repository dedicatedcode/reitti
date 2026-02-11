package com.dedicatedcode.reitti.dto;

import java.util.Optional;

public record MapMetadata(
        long minTimestamp,
        long maxTimestamp,
        long totalPoints,
        double minLat,
        double maxLat,
        double minLng,
        double maxLng,
        Optional<LocationPoint2> latestLocation
) {}