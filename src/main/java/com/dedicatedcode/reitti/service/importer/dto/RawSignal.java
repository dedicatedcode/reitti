package com.dedicatedcode.reitti.service.importer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RawSignal {
    
    @JsonProperty("position")
    private Position position;
    
    @JsonProperty("wifiScan")
    private WifiScan wifiScan;

    @JsonProperty("activityRecord")
    private ActivityRecord activityRecord;

    public Position getPosition() {
        return position;
    }
    
    public void setPosition(Position position) {
        this.position = position;
    }
    
    public WifiScan getWifiScan() {
        return wifiScan;
    }
    
    public void setWifiScan(WifiScan wifiScan) {
        this.wifiScan = wifiScan;
    }

    public ActivityRecord getActivityRecord() {
        return activityRecord;
    }

    public void setActivityRecord(ActivityRecord activityRecord) {
        this.activityRecord = activityRecord;
    }
}
