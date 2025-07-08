package com.dedicatedcode.reitti.service.importer.dto.ios;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties({"timelineMemory"})
public class IOSSemanticSegment {
    
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
    private IOSVisit visit;
    
    @JsonProperty("activity")
    private IOSActivity activity;
    
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
    
    public IOSVisit getVisit() {
        return visit;
    }
    
    public void setVisit(IOSVisit visit) {
        this.visit = visit;
    }
    
    public IOSActivity getActivity() {
        return activity;
    }
    
    public void setActivity(IOSActivity activity) {
        this.activity = activity;
    }
}
