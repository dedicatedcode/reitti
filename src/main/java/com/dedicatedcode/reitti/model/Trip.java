package com.dedicatedcode.reitti.model;

import java.time.Duration;
import java.time.Instant;

public class Trip {
    
    private Long id;
    private User user;
    private SignificantPlace startPlace;
    private SignificantPlace endPlace;
    private Instant startTime;
    private Instant endTime;
    private Long durationSeconds;
    private Double estimatedDistanceMeters;
    private Double travelledDistanceMeters;
    private String transportModeInferred;
    private ProcessedVisit startVisit;
    private ProcessedVisit endVisit;
    private Long version;

    public Trip() {}

    public Trip(User user, SignificantPlace startPlace, SignificantPlace endPlace, Instant startTime, Instant endTime, Double estimatedDistanceMeters, String transportModeInferred, ProcessedVisit startVisit, ProcessedVisit endVisit) {
        this.user = user;
        this.startPlace = startPlace;
        this.endPlace = endPlace;
        this.startTime = startTime;
        this.endTime = endTime;
        this.estimatedDistanceMeters = estimatedDistanceMeters;
        this.transportModeInferred = transportModeInferred;
        this.startVisit = startVisit;
        this.endVisit = endVisit;
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

    public SignificantPlace getStartPlace() {
        return startPlace;
    }

    public void setStartPlace(SignificantPlace startPlace) {
        this.startPlace = startPlace;
    }

    public SignificantPlace getEndPlace() {
        return endPlace;
    }

    public void setEndPlace(SignificantPlace endPlace) {
        this.endPlace = endPlace;
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

    public Double getEstimatedDistanceMeters() {
        return estimatedDistanceMeters;
    }

    public void setEstimatedDistanceMeters(Double estimatedDistanceMeters) {
        this.estimatedDistanceMeters = estimatedDistanceMeters;
    }
    
    public Double getTravelledDistanceMeters() {
        return travelledDistanceMeters;
    }

    public void setTravelledDistanceMeters(Double travelledDistanceMeters) {
        this.travelledDistanceMeters = travelledDistanceMeters;
    }

    public String getTransportModeInferred() {
        return transportModeInferred;
    }

    public void setTransportModeInferred(String transportModeInferred) {
        this.transportModeInferred = transportModeInferred;
    }

    public ProcessedVisit getStartVisit() {
        return startVisit;
    }

    public void setStartVisit(ProcessedVisit startVisit) {
        this.startVisit = startVisit;
    }

    public ProcessedVisit getEndVisit() {
        return endVisit;
    }

    public void setEndVisit(ProcessedVisit endVisit) {
        this.endVisit = endVisit;
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


}
