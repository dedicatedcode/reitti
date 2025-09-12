package com.dedicatedcode.reitti.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Objects;

public class LocationDataRequest {
    
    @NotEmpty
    private List<@Valid LocationPoint> points;

    public List<LocationPoint> getPoints() {
        return points;
    }

    public void setPoints(List<LocationPoint> points) {
        this.points = points;
    }

    @Override
    public String toString() {
        return "LocationDataRequest{" +
                "points=" + points +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        LocationDataRequest that = (LocationDataRequest) o;
        return Objects.equals(points, that.points);
    }

    @Override
    public int hashCode() {
        return Objects.hash(points);
    }

    public static class LocationPoint {
        @NotNull
        private Double latitude;
        
        @NotNull
        private Double longitude;
        
        @NotNull
        private String timestamp; // ISO8601 format
        
        @NotNull
        private Double accuracyMeters;
        
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

        @Override
        public String toString() {
            return "LocationPoint{" +
                    "latitude=" + latitude +
                    ", longitude=" + longitude +
                    ", timestamp='" + timestamp + '\'' +
                    ", accuracyMeters=" + accuracyMeters +
                    '}';
        }
    }
}
