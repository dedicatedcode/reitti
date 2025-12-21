package com.dedicatedcode.reitti.dto;

import com.dedicatedcode.reitti.model.geo.GeoPoint;

import java.util.List;

public class ProcessedVisitResponse {
    
    public static class PlaceInfo {
        private final Long id;
        private final String name;
        private final String address;
        private final String city;
        private final String countryCode;
        private final Double lat;
        private final Double lng;
        private final String type;
        private final List<GeoPoint> polygon;
        
        public PlaceInfo(Long id, String name, String address, String city, String countryCode, Double lat, Double lng, String type, List<GeoPoint> polygon) {
            this.id = id;
            this.name = name;
            this.address = address;
            this.city = city;
            this.countryCode = countryCode;
            this.lat = lat;
            this.lng = lng;
            this.type = type;
            this.polygon = polygon;
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
        
        public String getCity() {
            return city;
        }
        
        public String getCountryCode() {
            return countryCode;
        }
        
        public Double getLat() {
            return lat;
        }
        
        public Double getLng() {
            return lng;
        }
        
        public String getType() {
            return type;
        }

        public List<GeoPoint> getPolygon() { return polygon; }
    }
    
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
            this.lat = place.getLat();
            this.lng = place.getLng();
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
