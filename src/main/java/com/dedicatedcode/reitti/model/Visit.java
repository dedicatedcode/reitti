package com.dedicatedcode.reitti.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class Visit {

    private Long id;
    private User user;
    private Double longitude;
    private Double latitude;
    private Instant startTime;
    private Instant endTime;
    private Long durationSeconds;
    private boolean processed = false;
    private Long version;

    public Visit() {
    }

    public Visit(User user, Double longitude, Double latitude, Instant startTime, Instant endTime) {
        this.user = user;
        this.longitude = longitude;
        this.latitude = latitude;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public Long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public void calculateDuration() {
        if (startTime != null && endTime != null) {
            durationSeconds = Duration.between(startTime, endTime).getSeconds();
        }
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
