package com.dedicatedcode.reitti.dto;

public record MapMetadata(
    long minTimestamp,
    long maxTimestamp,
    long totalPoints,
    double minLat,
    double maxLat,
    double minLng,
    double maxLng
) {}