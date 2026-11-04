package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.geo.TransportMode;

import java.util.List;

public record TripSegmentDTO(
        long offsetSeconds,
        long durationSeconds,
        TransportMode mode,
        double confidence,
        String color,
        String icon
) {
}
