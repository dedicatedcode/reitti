package com.dedicatedcode.reitti.model;

import java.time.Instant;

public class Trip {
    
    private final Long id;
    private final Instant startTime;
    private final Instant endTime;
    private final Long durationSeconds;
    private final Double estimatedDistanceMeters;
    private final Double travelledDistanceMeters;
    private final String transportModeInferred;
    private final ProcessedVisit startVisit;
    private final ProcessedVisit endVisit;
    private final Long version;

    public Trip(Instant startTime, Instant endTime, Long durationSeconds, Double estimatedDistanceMeters, Double travelledDistanceMeters, String transportModeInferred, ProcessedVisit startVisit, ProcessedVisit endVisit) {
        this(null, startTime, endTime, durationSeconds, estimatedDistanceMeters, travelledDistanceMeters, transportModeInferred, startVisit, endVisit, 1L);
    }
    
    public Trip(Long id, Instant startTime, Instant endTime, Long durationSeconds, Double estimatedDistanceMeters, Double travelledDistanceMeters, String transportModeInferred, ProcessedVisit startVisit, ProcessedVisit endVisit, Long version) {
        this.id = id;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationSeconds = durationSeconds;
        this.estimatedDistanceMeters = estimatedDistanceMeters;
        this.travelledDistanceMeters = travelledDistanceMeters;
        this.transportModeInferred = transportModeInferred;
        this.startVisit = startVisit;
        this.endVisit = endVisit;
        this.version = version;
    }

    public Long getId() {
        return id;
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

    public Double getEstimatedDistanceMeters() {
        return estimatedDistanceMeters;
    }
    
    public Double getTravelledDistanceMeters() {
        return travelledDistanceMeters;
    }

    public String getTransportModeInferred() {
        return transportModeInferred;
    }

    public ProcessedVisit getStartVisit() {
        return startVisit;
    }

    public ProcessedVisit getEndVisit() {
        return endVisit;
    }

    public Long getVersion() {
        return version;
    }

    public Trip withId(Long id) {
        return new Trip(id, this.startTime, this.endTime, this.durationSeconds, this.estimatedDistanceMeters, this.travelledDistanceMeters, this.transportModeInferred, this.startVisit, this.endVisit, this.version);
    }
}
