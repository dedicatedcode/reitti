package com.dedicatedcode.reitti.model.geo;

import java.time.Instant;
import java.util.Objects;

public class Trip {
    
    private final Long id;
    private final Instant startTime;
    private final Instant endTime;
    private final Long durationSeconds;
    private final Double estimatedDistanceMeters;
    private final Double travelledDistanceMeters;
    private final TransportMode transportModeInferred;
    private final ProcessedVisit startVisit;
    private final ProcessedVisit endVisit;
    private final Long version;

    public Trip(Instant startTime, Instant endTime, Long durationSeconds, Double estimatedDistanceMeters, Double travelledDistanceMeters, TransportMode transportModeInferred, ProcessedVisit startVisit, ProcessedVisit endVisit) {
        this(null, startTime, endTime, durationSeconds, estimatedDistanceMeters, travelledDistanceMeters, transportModeInferred, startVisit, endVisit, 1L);
    }
    
    public Trip(Long id, Instant startTime, Instant endTime, Long durationSeconds, Double estimatedDistanceMeters, Double travelledDistanceMeters, TransportMode transportModeInferred, ProcessedVisit startVisit, ProcessedVisit endVisit, Long version) {
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

    public TransportMode getTransportModeInferred() {
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

    public Trip withTransportMode(TransportMode mode) {
        return new Trip(this.id, this.startTime, this.endTime, this.durationSeconds, this.estimatedDistanceMeters, this.travelledDistanceMeters, mode, this.startVisit, this.endVisit, this.version);
    }

    public Trip withVersion(long version) {
        return new Trip(id, this.startTime, this.endTime, this.durationSeconds, this.estimatedDistanceMeters, this.travelledDistanceMeters, this.transportModeInferred, this.startVisit, this.endVisit, version);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Trip trip = (Trip) o;
        return Objects.equals(id, trip.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

}
