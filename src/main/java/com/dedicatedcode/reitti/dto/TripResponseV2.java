package com.dedicatedcode.reitti.dto;

import com.dedicatedcode.reitti.controller.api.TripDTO;

import java.util.List;

public record TripResponseV2(
        long minTimestamp,
        long maxTimestamp,
        String color,
        List<TripDTO> trips
) {
}
