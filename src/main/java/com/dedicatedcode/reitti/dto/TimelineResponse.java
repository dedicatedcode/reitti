package com.dedicatedcode.reitti.dto;


import org.springframework.data.geo.Point;

import java.time.Instant;
import java.util.List;

public class TimelineResponse {
    private final List<TimelineEntry> entries;

    public TimelineResponse(List<TimelineEntry> entries) {
        this.entries = entries;
    }

    public List<TimelineEntry> getEntries() {
        return entries;
    }

    public static class TimelineEntry {
        private final String type; // "VISIT" or "TRIP"
        private final Long id;
        private final Instant startTime;
        private final Instant endTime;
        private final Long durationSeconds;
        
        // For visits
        private final PlaceInfo place;
        
        // For trips
        private final PlaceInfo startPlace;
        private final PlaceInfo endPlace;
        private final Double distanceMeters;
        private final String transportMode;
        private final List<PointInfo> path;

        public TimelineEntry(String type, Long id, Instant startTime, Instant endTime, Long durationSeconds, PlaceInfo place, PlaceInfo startPlace, PlaceInfo endPlace, Double distanceMeters, String transportMode, List<PointInfo> path) {
            this.type = type;
            this.id = id;
            this.startTime = startTime;
            this.endTime = endTime;
            this.durationSeconds = durationSeconds;
            this.place = place;
            this.startPlace = startPlace;
            this.endPlace = endPlace;
            this.distanceMeters = distanceMeters;
            this.transportMode = transportMode;
            this.path = path;
        }

        public String getType() {
            return type;
        }

        public Long getId() {
            return id;
        }

        public Instant getStartTime() {
            return startTime;
        }

        public Instant getEndTime() {
            return endTime;
        }

        public Long getDurationSeconds() {
            return durationSeconds;
        }

        public PlaceInfo getPlace() {
            return place;
        }

        public PlaceInfo getStartPlace() {
            return startPlace;
        }

        public PlaceInfo getEndPlace() {
            return endPlace;
        }

        public Double getDistanceMeters() {
            return distanceMeters;
        }

        public String getTransportMode() {
            return transportMode;
        }

        public List<PointInfo> getPath() {
            return path;
        }
    }

    public record PointInfo(Double latitude, Double longitude, Instant timestamp, Double accuracy) {
    }

    public record PlaceInfo(Long id, String name, String address, String category, Double latitude, Double longitude) {
    }
}
