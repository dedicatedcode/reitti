package com.dedicatedcode.reitti.model.geo;

import java.time.Instant;
import java.util.List;

public record H3Hexagon(String h3Index, Instant timestamp, int resolution, List<GeoPoint> points, GeoPoint foundBy) {}
