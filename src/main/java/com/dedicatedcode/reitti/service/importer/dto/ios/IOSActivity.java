package com.dedicatedcode.reitti.service.importer.dto.ios;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties({"topCandidate"})
public class IOSActivity {
    
    @JsonProperty("start")
    private String start;
    
    @JsonProperty("end")
    private String end;
    
    @JsonProperty("distanceMeters")
    private Double distanceMeters;

    public String getStart() {
        return start;
    }

    public void setStart(String start) {
        this.start = start;
    }

    public String getEnd() {
        return end;
    }

    public void setEnd(String end) {
        this.end = end;
    }

    public Double getDistanceMeters() {
        return distanceMeters;
    }
    
    public void setDistanceMeters(Double distanceMeters) {
        this.distanceMeters = distanceMeters;
    }
}
