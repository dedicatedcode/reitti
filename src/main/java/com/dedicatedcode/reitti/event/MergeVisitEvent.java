package com.dedicatedcode.reitti.event;

public class MergeVisitEvent {
    private String userName;
    private Long startTime;
    private Long endTime;

    // Default constructor for Jackson
    public MergeVisitEvent() {
    }

    public MergeVisitEvent(String userName, Long startTime, Long endTime) {
        this.userName = userName;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Long getEndTime() {
        return endTime;
    }

    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }
}