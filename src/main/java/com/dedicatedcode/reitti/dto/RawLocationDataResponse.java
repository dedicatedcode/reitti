package com.dedicatedcode.reitti.dto;

import java.util.List;

public record RawLocationDataResponse(List<Segment> segments, LocationPoint latest) {

    public record Segment(List<LocationPoint> points) {}
}
