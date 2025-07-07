package com.dedicatedcode.reitti.service.importer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Position {
    
    @JsonProperty("LatLng")
    private String latLng;
    
    @JsonProperty("accuracyMeters")
    private Integer accuracyMeters;
    
    @JsonProperty("altitudeMeters")
    private Double altitudeMeters;
    
    @JsonProperty("source")
    private String source;
    
    @JsonProperty("timestamp")
    private String timestamp;
    
    @JsonProperty("speedMetersPerSecond")
    private Double speedMetersPerSecond;
    
    public String getLatLng() {
        return latLng;
    }
    
    public void setLatLng(String latLng) {
        this.latLng = latLng;
    }
    
    public Integer getAccuracyMeters() {
        return accuracyMeters;
    }
    
    public void setAccuracyMeters(Integer accuracyMeters) {
        this.accuracyMeters = accuracyMeters;
    }
    
    public Double getAltitudeMeters() {
        return altitudeMeters;
    }
    
    public void setAltitudeMeters(Double altitudeMeters) {
        this.altitudeMeters = altitudeMeters;
    }
    
    public String getSource() {
        return source;
    }
    
    public void setSource(String source) {
        this.source = source;
    }
    
    public String getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    
    public Double getSpeedMetersPerSecond() {
        return speedMetersPerSecond;
    }
    
    public void setSpeedMetersPerSecond(Double speedMetersPerSecond) {
        this.speedMetersPerSecond = speedMetersPerSecond;
    }
}
