package com.dedicatedcode.reitti.model.geo;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public class ProcessedVisit {

    private final Long id;
    private final SignificantPlace place;
    private final Instant startTime;
    private final Instant endTime;
    private final Long durationSeconds;
    private final Map<String, Object> metadata;
    private final Long version;

    public ProcessedVisit(SignificantPlace place, Instant startTime, Instant endTime, Long durationSeconds, Map<String, Object> metadata) {
        this(null, place, startTime, endTime, durationSeconds, metadata, 1L);
    }

    public ProcessedVisit(Long id, SignificantPlace place, Instant startTime, Instant endTime, Long durationSeconds, Map<String, Object> metadata, Long version) {
        this.id = id;
        this.place = place;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationSeconds = durationSeconds;
        this.metadata = metadata;
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public SignificantPlace getPlace() {
        return place;
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

    public Long getVersion() {
        return this.version;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public ProcessedVisit withId(Long id) {
        return new ProcessedVisit(id, this.place, this.startTime, this.endTime, this.durationSeconds, metadata, this.version);
    }

    public ProcessedVisit withVersion(long version) {
        return new ProcessedVisit(this.id, this.place, this.startTime, this.endTime, this.durationSeconds, metadata, version);
    }

    public ProcessedVisit withMetadata(Map<String, Object> metadata) {
        return new ProcessedVisit(this.id, this.place, this.startTime, this.endTime, this.durationSeconds, metadata, this.version);
    }

    @Override
    public String toString() {
        return "ProcessedVisit{" +
                "id=" + id +
                ", place=" + place +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", durationSeconds=" + durationSeconds +
                ", version=" + version +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ProcessedVisit that = (ProcessedVisit) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
