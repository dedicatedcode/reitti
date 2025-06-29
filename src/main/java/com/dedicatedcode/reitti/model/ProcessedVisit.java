package com.dedicatedcode.reitti.model;

import java.time.Instant;
import java.util.List;

public class ProcessedVisit {

    private final Long id;
    private final SignificantPlace place;
    private final Instant startTime;
    private final Instant endTime;
    private final Long durationSeconds;
    private final Long version;

    public ProcessedVisit(SignificantPlace place, Instant startTime, Instant endTime, Long durationSeconds) {
        this(null, place, startTime, endTime, durationSeconds, 1L);
    }

    public ProcessedVisit(Long id, SignificantPlace place, Instant startTime, Instant endTime, Long durationSeconds, Long version) {
        this.id = id;
        this.place = place;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationSeconds = durationSeconds;
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

    public ProcessedVisit withId(Long id) {
        return new ProcessedVisit(id, this.place, this.startTime, this.endTime, this.durationSeconds, this.version);
    }

    public ProcessedVisit withVersion(long version) {
        return new ProcessedVisit(this.id, this.place, this.startTime, this.endTime, this.durationSeconds, version);
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
}
