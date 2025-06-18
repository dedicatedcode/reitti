package com.dedicatedcode.reitti.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class Visit {

    private final Long id;
    private final User user;
    private final Double longitude;
    private final Double latitude;
    private final Instant startTime;
    private final Instant endTime;
    private final Long durationSeconds;
    private final boolean processed;
    private final Long version;

    public Visit() {
        this(null, null, null, null, null, null, false, null);
    }

    public Visit(User user, Double longitude, Double latitude, Instant startTime, Instant endTime) {
        this(null, user, longitude, latitude, startTime, endTime, false, null);
    }
    
    public Visit(Long id, User user, Double longitude, Double latitude, Instant startTime, Instant endTime, boolean processed, Long version) {
        this.id = id;
        this.user = user;
        this.longitude = longitude;
        this.latitude = latitude;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationSeconds = (startTime != null && endTime != null) ? 
            Duration.between(startTime, endTime).getSeconds() : null;
        this.processed = processed;
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Double getLongitude() {
        return longitude;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public Long getDurationSeconds() {
        return durationSeconds;
    }

    public boolean isProcessed() {
        return processed;
    }

    public Long getVersion() {
        return version;
    }
    
    // Wither method
    public Visit withProcessed(boolean processed) {
        return new Visit(this.id, this.user, this.longitude, this.latitude, this.startTime, this.endTime, processed, this.version);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Visit visit = (Visit) o;
        return Objects.equals(id, visit.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
