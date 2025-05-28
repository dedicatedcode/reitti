package com.dedicatedcode.reitti.event;

public class MergeVisitEvent {
    private Long userId;
    private Long startTime;
    private Long endTime;

    // Default constructor for Jackson
    public MergeVisitEvent() {
    }

    public MergeVisitEvent(Long userId, Long startTime, Long endTime) {
        this.userId = userId;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    // Getters and setters
    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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