package com.dedicatedcode.reitti.event;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import java.time.Instant;
import java.util.List;

public class LocationDataEvent {
    private final Long userId;
    private final String username;
    private final List<LocationDataRequest.LocationPoint> points;
    private final Instant receivedAt;

    public LocationDataEvent(Long userId, String username, List<LocationDataRequest.LocationPoint> points) {
        this.userId = userId;
        this.username = username;
        this.points = points;
        this.receivedAt = Instant.now();
    }

    public Long getUserId() {
        return userId;
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
