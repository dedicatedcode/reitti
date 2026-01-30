package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.geo.TransportMode;

import java.util.List;

// Individual Trip Object
public record TripDTO(
        long id,
        List<double[]> path,
        List<Long> timestamps,
        TransportMode mode
) {
}
