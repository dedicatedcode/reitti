package com.dedicatedcode.reitti.dto;

import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;

import java.util.List;

public class ProcessedVisitResponse {
    
    public static class PlaceVisitSummary {
        private final SignificantPlace place;
        private final List<VisitDetail> visits;
        private final long totalDurationSeconds;
        private final int visitCount;
        
        public PlaceVisitSummary(SignificantPlace place, List<VisitDetail> visits, long totalDurationSeconds, int visitCount) {
            this.place = place;
            this.visits = visits;
            this.totalDurationSeconds = totalDurationSeconds;
            this.visitCount = visitCount;
        }
        
        public SignificantPlace getPlace() {
            return place;
        }
        
        public List<VisitDetail> getVisits() {
            return visits;
        }
        
        public long getTotalDurationSeconds() {
            return totalDurationSeconds;
        }
        
        public int getVisitCount() {
            return visitCount;
        }
    }
    
    public static class VisitDetail {
        private final Long id;
        private final String startTime;
        private final String endTime;
        private final long durationSeconds;
        
        public VisitDetail(ProcessedVisit visit) {
            this.id = visit.getId();
            this.startTime = visit.getStartTime().toString();
            this.endTime = visit.getEndTime().toString();
            this.durationSeconds = visit.getDurationSeconds();
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
