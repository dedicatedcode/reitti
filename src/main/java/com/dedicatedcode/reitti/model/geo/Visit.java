package com.dedicatedcode.reitti.model.geo;

import java.time.Instant;
import java.util.Objects;

public class Visit {

    private final Long id;
    private final Double longitude;
    private final Double latitude;
    private final Instant startTime;
    private final Instant endTime;
    private final Long durationSeconds;
    private final boolean processed;
    private final Long version;

    public Visit(Double longitude, Double latitude, Instant startTime, Instant endTime, Long durationSeconds, boolean processed) {
        this(null, longitude, latitude, startTime, endTime, durationSeconds, processed, 1L);
    }
    public Visit(Long id, Double longitude, Double latitude, Instant startTime, Instant endTime, Long durationSeconds, boolean processed, Long version) {
        this.id = id;
        this.longitude = longitude;
        this.latitude = latitude;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationSeconds = durationSeconds;
        this.processed = processed;
        this.version = version;
    }

    public Long getId() {
        return id;
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

    public Visit withId(Long id) {
        return new Visit(id, longitude, latitude, startTime, endTime, durationSeconds, processed, version);
    }

    public Visit withVersion(long version) {
        return new Visit(this.id, this.longitude, this.latitude, this.startTime, this.endTime, durationSeconds, processed, version);
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

    @Override
    public String toString() {
        return "Visit{" +
                "latitude=" + latitude +
                ", id=" + id +
                ", longitude=" + longitude +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", durationSeconds=" + durationSeconds +
                ", processed=" + processed +
                ", version=" + version +
                '}';
    }
}
