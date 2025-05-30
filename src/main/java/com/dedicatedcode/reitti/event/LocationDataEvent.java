package com.dedicatedcode.reitti.event;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public class LocationDataEvent implements Serializable {
    private final String username;
    private final List<LocationDataRequest.LocationPoint> points;
    private final Instant receivedAt;

    @JsonCreator
    public LocationDataEvent(
            @JsonProperty("username") String username,
            @JsonProperty("points") List<LocationDataRequest.LocationPoint> points) {
        this.username = username;
        this.points = points;
        this.receivedAt = Instant.now();
    }

    public String getUsername() {
        return username;
    }

    public List<LocationDataRequest.LocationPoint> getPoints() {
        return points;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
