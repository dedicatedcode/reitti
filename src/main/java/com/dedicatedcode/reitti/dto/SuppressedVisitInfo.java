package com.dedicatedcode.reitti.dto;

import java.time.Instant;

public record SuppressedVisitInfo(Long id, Long placeId, String placeName, Double latitudeCentroid,
                                  Double longitudeCentroid, Instant startTime, Instant endTime) {
}
