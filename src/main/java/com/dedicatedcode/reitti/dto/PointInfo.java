package com.dedicatedcode.reitti.dto;

import java.time.Instant;

public record PointInfo(Double latitude, Double longitude, Instant timestamp, Double accuracy) {
}
