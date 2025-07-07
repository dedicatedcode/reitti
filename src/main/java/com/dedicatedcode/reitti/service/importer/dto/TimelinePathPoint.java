package com.dedicatedcode.reitti.service.importer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TimelinePathPoint {
    
    @JsonProperty("point")
    private String point;
    
    @JsonProperty("time")
    private String time;
    
    public String getPoint() {
        return point;
    }
    
    public void setPoint(String point) {
        this.point = point;
    }
    
    public String getTime() {
        return time;
    }
    
    public void setTime(String time) {
        this.time = time;
    }
}
