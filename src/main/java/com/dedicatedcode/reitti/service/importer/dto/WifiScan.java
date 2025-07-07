package com.dedicatedcode.reitti.service.importer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class WifiScan {
    
    @JsonProperty("deliveryTime")
    private String deliveryTime;
    
    @JsonProperty("devicesRecords")
    private List<DeviceRecord> devicesRecords;
    
    public String getDeliveryTime() {
        return deliveryTime;
    }
    
    public void setDeliveryTime(String deliveryTime) {
        this.deliveryTime = deliveryTime;
    }
    
    public List<DeviceRecord> getDevicesRecords() {
        return devicesRecords;
    }
    
    public void setDevicesRecords(List<DeviceRecord> devicesRecords) {
        this.devicesRecords = devicesRecords;
    }
}
