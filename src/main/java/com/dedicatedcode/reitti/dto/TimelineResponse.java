package com.dedicatedcode.reitti.dto;


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

        public TimelineEntry(String type, Long id, Instant startTime, Instant endTime, Long durationSeconds, PlaceInfo place, PlaceInfo startPlace, PlaceInfo endPlace, Double distanceMeters, String transportMode) {
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

        public static class PlaceInfo {
            private final Long id;
            private final String name;
            private final String address;
            private final String category;
            private final Double latitude;
            private final Double longitude;

            public PlaceInfo(Long id, String name, String address, String category, Double latitude, Double longitude) {
                this.id = id;
                this.name = name;
                this.address = address;
                this.category = category;
                this.latitude = latitude;
                this.longitude = longitude;
            }

            public Long getId() {
                return id;
            }

            public String getName() {
                return name;
            }

            public String getAddress() {
                return address;
            }

            public String getCategory() {
                return category;
            }

            public Double getLatitude() {
                return latitude;
            }

            public Double getLongitude() {
                return longitude;
            }
        }
    }
}
