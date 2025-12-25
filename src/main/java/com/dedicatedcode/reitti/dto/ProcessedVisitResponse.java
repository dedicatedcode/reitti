package com.dedicatedcode.reitti.dto;

import java.util.List;

public class ProcessedVisitResponse {
    
    public static class PlaceVisitSummary {
        private final PlaceInfo place;
        private final List<VisitDetail> visits;
        private final long totalDurationMs;
        private final int visitCount;
        private final String color;
        private final Double lat;
        private final Double lng;
        
        public PlaceVisitSummary(PlaceInfo place, List<VisitDetail> visits, long totalDurationMs, int visitCount, String color) {
            this.place = place;
            this.visits = visits;
            this.totalDurationMs = totalDurationMs;
            this.visitCount = visitCount;
            this.color = color;
            this.lat = place.lat();
            this.lng = place.lng();
        }
        
        public PlaceInfo getPlace() {
            return place;
        }
        
        public List<VisitDetail> getVisits() {
            return visits;
        }
        
        public long getTotalDurationMs() {
            return totalDurationMs;
        }
        
        public int getVisitCount() {
            return visitCount;
        }
        
        public String getColor() {
            return color;
        }
        
        public Double getLat() {
            return lat;
        }
        
        public Double getLng() {
            return lng;
        }
    }
    
    public static class VisitDetail {
        private final Long id;
        private final String startTime;
        private final String endTime;
        private final long durationSeconds;
        
        public VisitDetail(Long id, String startTime, String endTime, long durationSeconds) {
            this.id = id;
            this.startTime = startTime;
            this.endTime = endTime;
            this.durationSeconds = durationSeconds;
        }
        
        public Long getId() {
            return id;
        }
        
        public String getStartTime() {
            return startTime;
        }
        
        public String getEndTime() {
            return endTime;
        }
        
        public long getDurationSeconds() {
            return durationSeconds;
        }
    }
    
    private final List<PlaceVisitSummary> places;
    
    public ProcessedVisitResponse(List<PlaceVisitSummary> places) {
        this.places = places;
    }
    
    public List<PlaceVisitSummary> getPlaces() {
        return places;
    }
}
