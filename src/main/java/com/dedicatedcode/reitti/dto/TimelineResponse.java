package com.dedicatedcode.reitti.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimelineResponse {
    private List<TimelineEntry> entries;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelineEntry {
        private String type; // "VISIT" or "TRIP"
        private Long id;
        private Instant startTime;
        private Instant endTime;
        private Long durationSeconds;
        
        // For visits
        private PlaceInfo place;
        
        // For trips
        private PlaceInfo startPlace;
        private PlaceInfo endPlace;
        private Double distanceMeters;
        private String transportMode;
        
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class PlaceInfo {
            private Long id;
            private String name;
            private String address;
            private String category;
            private Double latitude;
            private Double longitude;
        }
    }
}
