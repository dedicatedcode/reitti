package com.dedicatedcode.reitti.model.geo;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Trip {
    
    private final Long id;
    private final Instant startTime;
    private final Instant endTime;
    private final Long durationSeconds;
    private final Double estimatedDistanceMeters;
    private final Double travelledDistanceMeters;
    private final List<TransportModeSegment> segments;
    private final ProcessedVisit startVisit;
    private final ProcessedVisit endVisit;
    private final Map<String, Object> metadata;
    private final Long version;

    public Trip(Instant startTime, Instant endTime, Long durationSeconds, Double estimatedDistanceMeters, Double travelledDistanceMeters, TransportMode transportModeInferred, ProcessedVisit startVisit, ProcessedVisit endVisit, Map<String, Object> metadata) {
        this(null, startTime, endTime, durationSeconds, estimatedDistanceMeters, travelledDistanceMeters, toSegments(transportModeInferred, durationSeconds, travelledDistanceMeters), startVisit, endVisit, metadata, 1L);
    }

    public Trip(Instant startTime, Instant endTime, Long durationSeconds, Double estimatedDistanceMeters, Double travelledDistanceMeters, List<TransportModeSegment> segments, ProcessedVisit startVisit, ProcessedVisit endVisit, Map<String, Object> metadata) {
        this(null, startTime, endTime, durationSeconds, estimatedDistanceMeters, travelledDistanceMeters, segments, startVisit, endVisit, metadata, 1L);
    }
    
    public Trip(Long id, Instant startTime, Instant endTime, Long durationSeconds, Double estimatedDistanceMeters, Double travelledDistanceMeters, TransportMode transportModeInferred, ProcessedVisit startVisit, ProcessedVisit endVisit, Map<String, Object> metadata, Long version) {
        this(id, startTime, endTime, durationSeconds, estimatedDistanceMeters, travelledDistanceMeters, toSegments(transportModeInferred, durationSeconds, travelledDistanceMeters), startVisit, endVisit, metadata, version);
    }

    public Trip(Long id, Instant startTime, Instant endTime, Long durationSeconds, Double estimatedDistanceMeters, Double travelledDistanceMeters, List<TransportModeSegment> segments, ProcessedVisit startVisit, ProcessedVisit endVisit, Map<String, Object> metadata, Long version) {
        this.id = id;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationSeconds = durationSeconds;
        this.estimatedDistanceMeters = estimatedDistanceMeters;
        this.travelledDistanceMeters = travelledDistanceMeters;
        this.segments = segments;
        this.startVisit = startVisit;
        this.endVisit = endVisit;
        this.metadata = metadata;
        this.version = version;
    }

    private static List<TransportModeSegment> toSegments(TransportMode mode, Long durationSeconds, Double travelledDistanceMeters) {
        if (mode == null) {
            return List.of();
        }
        return List.of(new TransportModeSegment(mode, 0L, durationSeconds == null ? 0L : durationSeconds, travelledDistanceMeters == null ? 0.0 : travelledDistanceMeters));
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
        return getDominantMode();
    }

    public List<TransportModeSegment> getSegments() {
        return segments;
    }

    public TransportMode getDominantMode() {
        return segments.stream()
                .collect(java.util.stream.Collectors.groupingBy(TransportModeSegment::mode, java.util.stream.Collectors.summingLong(TransportModeSegment::durationSeconds)))
                .entrySet().stream()
                .max(Comparator.comparingLong(e -> e.getValue()))
                .map(Map.Entry::getKey)
                .orElse(null);
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

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Trip withId(Long id) {
        return new Trip(id, this.startTime, this.endTime, this.durationSeconds, this.estimatedDistanceMeters, this.travelledDistanceMeters, this.segments, this.startVisit, this.endVisit, metadata, this.version);
    }

    public Trip withTransportMode(TransportMode mode) {
        return new Trip(this.id, this.startTime, this.endTime, this.durationSeconds, this.estimatedDistanceMeters, this.travelledDistanceMeters, toSegments(mode, this.durationSeconds, this.travelledDistanceMeters), this.startVisit, this.endVisit, metadata, this.version);
    }

    public Trip withSegments(List<TransportModeSegment> segments) {
        return new Trip(this.id, this.startTime, this.endTime, this.durationSeconds, this.estimatedDistanceMeters, this.travelledDistanceMeters, segments, this.startVisit, this.endVisit, metadata, this.version);
    }

    public Trip withVersion(long version) {
        return new Trip(id, this.startTime, this.endTime, this.durationSeconds, this.estimatedDistanceMeters, this.travelledDistanceMeters, this.segments, this.startVisit, this.endVisit, metadata, version);
    }

    public Trip withMetadata(Map<String, Object> metadata) {
        return new Trip(id, this.startTime, this.endTime, this.durationSeconds, this.estimatedDistanceMeters, this.travelledDistanceMeters, this.segments, this.startVisit, this.endVisit, metadata, this.version);
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
