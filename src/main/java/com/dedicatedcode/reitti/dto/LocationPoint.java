package com.dedicatedcode.reitti.dto;

import jakarta.validation.constraints.NotNull;

public class LocationPoint {
    @NotNull
    private Double latitude;

    @NotNull
    private Double longitude;

    @NotNull
    private String timestamp; // ISO8601 format

    @NotNull
    private Double accuracyMeters;

    private Double elevationMeters;

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

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Double getAccuracyMeters() {
        return accuracyMeters;
    }

    public void setAccuracyMeters(Double accuracyMeters) {
        this.accuracyMeters = accuracyMeters;
    }

    public Double getElevationMeters() {
        return elevationMeters;
    }

    public void setElevationMeters(Double elevationMeters) {
        this.elevationMeters = elevationMeters;
    }

    @Override
    public String toString() {
        return "LocationPoint{" +
                "latitude=" + latitude +
                ", longitude=" + longitude +
                ", timestamp='" + timestamp + '\'' +
                ", accuracyMeters=" + accuracyMeters +
                ", elevationMeters=" + elevationMeters +
                '}';
    }
}
