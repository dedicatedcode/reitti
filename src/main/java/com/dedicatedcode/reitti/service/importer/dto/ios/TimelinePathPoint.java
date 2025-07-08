package com.dedicatedcode.reitti.service.importer.dto.ios;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TimelinePathPoint {
    
    @JsonProperty("point")
    private String point;
    
    @JsonProperty("durationMinutesOffsetFromStartTime")
    private String durationMinutesOffsetFromStartTime;
    
    public String getPoint() {
        return point;
    }
    
    public void setPoint(String point) {
        this.point = point;
    }

    public String getDurationMinutesOffsetFromStartTime() {
        return durationMinutesOffsetFromStartTime;
    }

    public void setDurationMinutesOffsetFromStartTime(String durationMinutesOffsetFromStartTime) {
        this.durationMinutesOffsetFromStartTime = durationMinutesOffsetFromStartTime;
    }
}
