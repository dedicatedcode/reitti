package com.dedicatedcode.reitti.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OwntracksLocationRequest {
    
    @JsonProperty("_type")
    private String type;
    
    @JsonProperty("t")
    private String t;
    
    @JsonProperty("acc")
    private Double accuracy;
    
    @JsonProperty("alt")
    private Double altitude;
    
    @JsonProperty("batt")
    private Double battery;
    
    @JsonProperty("bs")
    private Boolean batteryStatus;
    
    @JsonProperty("lat")
    private Double latitude;
    
    @JsonProperty("lon")
    private Double longitude;
    
    @JsonProperty("tst")
    private Long timestamp;
    
    @JsonProperty("vel")
    private Double velocity;
    
    // Getters and setters
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getT() {
        return t;
    }
    
    public void setT(String t) {
        this.t = t;
    }
    
    public Double getAccuracy() {
        return accuracy;
    }
    
    public void setAccuracy(Double accuracy) {
        this.accuracy = accuracy;
    }
    
    public Double getAltitude() {
        return altitude;
    }
    
    public void setAltitude(Double altitude) {
        this.altitude = altitude;
    }
    
    public Double getBattery() {
        return battery;
    }
    
    public void setBattery(Double battery) {
        this.battery = battery;
    }
    
    public Boolean getBatteryStatus() {
        return batteryStatus;
    }
    
    public void setBatteryStatus(Boolean batteryStatus) {
        this.batteryStatus = batteryStatus;
    }
    
    public Double getLatitude() {
        return latitude;
    }
    
    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }
    
    public Double getLongitude() {
        return longitude;
    }
    
    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }
    
    public Long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
    
    public Double getVelocity() {
        return velocity;
    }
    
    public void setVelocity(Double velocity) {
        this.velocity = velocity;
    }
    
    /**
     * Checks if this is a location update
     */
    public boolean isLocationUpdate() {
        return "location".equals(type);
    }
    
    /**
     * Converts to a LocationPoint for processing
     */
    public LocationDataRequest.LocationPoint toLocationPoint() {
        LocationDataRequest.LocationPoint point = new LocationDataRequest.LocationPoint();
        point.setLatitude(latitude);
        point.setLongitude(longitude);
        
        // Convert Unix timestamp to ISO8601 format
        if (timestamp != null) {
            // Convert seconds to milliseconds and format as ISO8601
            String isoTimestamp = java.time.Instant.ofEpochSecond(timestamp).toString();
            point.setTimestamp(isoTimestamp);
        }
        
        if (accuracy != null) {
            point.setAccuracyMeters(accuracy);
        }
        
        return point;
    }
}
