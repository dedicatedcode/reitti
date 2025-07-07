package com.dedicatedcode.reitti.service.importer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Activity {
    
    @JsonProperty("start")
    private ActivityLocation start;
    
    @JsonProperty("end")
    private ActivityLocation end;
    
    @JsonProperty("distanceMeters")
    private Double distanceMeters;
    
    @JsonProperty("topCandidate")
    private TopCandidate topCandidate;
    
    public ActivityLocation getStart() {
        return start;
    }
    
    public void setStart(ActivityLocation start) {
        this.start = start;
    }
    
    public ActivityLocation getEnd() {
        return end;
    }
    
    public void setEnd(ActivityLocation end) {
        this.end = end;
    }
    
    public Double getDistanceMeters() {
        return distanceMeters;
    }
    
    public void setDistanceMeters(Double distanceMeters) {
        this.distanceMeters = distanceMeters;
    }
    
    public TopCandidate getTopCandidate() {
        return topCandidate;
    }
    
    public void setTopCandidate(TopCandidate topCandidate) {
        this.topCandidate = topCandidate;
    }
}
