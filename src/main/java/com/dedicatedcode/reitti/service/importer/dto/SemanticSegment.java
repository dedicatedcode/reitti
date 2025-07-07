package com.dedicatedcode.reitti.service.importer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class SemanticSegment {
    
    @JsonProperty("startTime")
    private String startTime;
    
    @JsonProperty("endTime")
    private String endTime;
    
    @JsonProperty("startTimeTimezoneUtcOffsetMinutes")
    private Integer startTimeTimezoneUtcOffsetMinutes;
    
    @JsonProperty("endTimeTimezoneUtcOffsetMinutes")
    private Integer endTimeTimezoneUtcOffsetMinutes;
    
    @JsonProperty("timelinePath")
    private List<TimelinePathPoint> timelinePath;
    
    @JsonProperty("visit")
    private Visit visit;
    
    @JsonProperty("activity")
    private Activity activity;
    
    public String getStartTime() {
        return startTime;
    }
    
    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }
    
    public String getEndTime() {
        return endTime;
    }
    
    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }
    
    public Integer getStartTimeTimezoneUtcOffsetMinutes() {
        return startTimeTimezoneUtcOffsetMinutes;
    }
    
    public void setStartTimeTimezoneUtcOffsetMinutes(Integer startTimeTimezoneUtcOffsetMinutes) {
        this.startTimeTimezoneUtcOffsetMinutes = startTimeTimezoneUtcOffsetMinutes;
    }
    
    public Integer getEndTimeTimezoneUtcOffsetMinutes() {
        return endTimeTimezoneUtcOffsetMinutes;
    }
    
    public void setEndTimeTimezoneUtcOffsetMinutes(Integer endTimeTimezoneUtcOffsetMinutes) {
        this.endTimeTimezoneUtcOffsetMinutes = endTimeTimezoneUtcOffsetMinutes;
    }
    
    public List<TimelinePathPoint> getTimelinePath() {
        return timelinePath;
    }
    
    public void setTimelinePath(List<TimelinePathPoint> timelinePath) {
        this.timelinePath = timelinePath;
    }
    
    public Visit getVisit() {
        return visit;
    }
    
    public void setVisit(Visit visit) {
        this.visit = visit;
    }
    
    public Activity getActivity() {
        return activity;
    }
    
    public void setActivity(Activity activity) {
        this.activity = activity;
    }
}
