package com.dedicatedcode.reitti.model;

import java.time.Duration;
import java.time.Instant;

public class Trip {
    
    private final Long id;
    private final User user;
    private final SignificantPlace startPlace;
    private final SignificantPlace endPlace;
    private final Instant startTime;
    private final Instant endTime;
    private final Long durationSeconds;
    private final Double estimatedDistanceMeters;
    private final Double travelledDistanceMeters;
    private final String transportModeInferred;
    private final ProcessedVisit startVisit;
    private final ProcessedVisit endVisit;
    private final Long version;

    public Trip() {
        this(null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public Trip(User user, SignificantPlace startPlace, SignificantPlace endPlace, Instant startTime, Instant endTime, Double estimatedDistanceMeters, String transportModeInferred, ProcessedVisit startVisit, ProcessedVisit endVisit) {
        this(null, user, startPlace, endPlace, startTime, endTime, estimatedDistanceMeters, null, transportModeInferred, startVisit, endVisit, null);
    }
    
    public Trip(Long id, User user, SignificantPlace startPlace, SignificantPlace endPlace, Instant startTime, Instant endTime, Double estimatedDistanceMeters, Double travelledDistanceMeters, String transportModeInferred, ProcessedVisit startVisit, ProcessedVisit endVisit, Long version) {
        this.id = id;
        this.user = user;
        this.startPlace = startPlace;
        this.endPlace = endPlace;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationSeconds = (startTime != null && endTime != null) ? 
            Duration.between(startTime, endTime).getSeconds() : null;
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

    public User getUser() {
        return user;
    }

    public SignificantPlace getStartPlace() {
        return startPlace;
    }

    public SignificantPlace getEndPlace() {
        return endPlace;
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


}
