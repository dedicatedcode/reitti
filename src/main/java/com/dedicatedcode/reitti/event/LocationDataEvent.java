package com.dedicatedcode.reitti.event;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public class LocationDataEvent implements Serializable {
    private final String username;
    private final List<LocationPoint> points;
    private final String traceId;
    private final Instant receivedAt;

    @JsonCreator
    public LocationDataEvent(
            @JsonProperty("username") String username,
            @JsonProperty("points") List<LocationPoint> points,
            @JsonProperty("trace-id") String traceId) {
        this.username = username;
        this.points = points;
        this.traceId = traceId;
        this.receivedAt = Instant.now();
    }

    public String getUsername() {
        return username;
    }

    public List<LocationPoint> getPoints() {
        return points;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
    public String getTraceId() {
        return traceId;
    }

    @Override
    public String toString() {
        return "LocationDataEvent{" +
                "username='" + username + '\'' +
                ", points=" + points.size() +
                ", traceId='" + traceId + '\'' +
                ", receivedAt=" + receivedAt +
                '}';
    }
}
