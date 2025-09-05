package com.dedicatedcode.reitti.model.geo;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class StayPoint {
    private final double latitude;
    private final double longitude;
    private final Instant arrivalTime;
    private final Instant departureTime;
    private final List<RawLocationPoint> points;
    
    public StayPoint(double latitude, double longitude, Instant arrivalTime, Instant departureTime, List<RawLocationPoint> points) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.arrivalTime = arrivalTime;
        this.departureTime = departureTime;
        this.points = points;
    }
    
    public double getLatitude() {
        return latitude;
    }
    
    public double getLongitude() {
        return longitude;
    }
    
    public Instant getArrivalTime() {
        return arrivalTime;
    }
    
    public Instant getDepartureTime() {
        return departureTime;
    }
    
    public List<RawLocationPoint> getPoints() {
        return points;
    }
    
    public long getDurationSeconds() {
        return Duration.between(arrivalTime, departureTime).getSeconds();
    }
}
