package com.dedicatedcode.reitti.service.importer.dto;

import java.util.List;

public class ActivityRecord {
    private List<PropableActivity> probableActivities;
    private String timestamp;

    public List<PropableActivity> getProbableActivities() {
        return probableActivities;
    }

    public void setProbableActivities(List<PropableActivity> probableActivities) {
        this.probableActivities = probableActivities;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
