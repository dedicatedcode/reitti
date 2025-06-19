package com.dedicatedcode.reitti.model;

import java.time.Instant;

public class ProcessedVisit {
    
    private final Long id;
    private final SignificantPlace place;
    private final Instant startTime;
    private final Instant endTime;
    private final Long durationSeconds;
    private final String originalVisitIds; // Comma-separated list of original visit IDs
    private final Integer mergedCount;
    private final Long version;

    public ProcessedVisit(SignificantPlace place, Instant startTime, Instant endTime, String originalVisitIds, Long durationSeconds, Integer mergedCount) {
        this(null, place, startTime, endTime, originalVisitIds, durationSeconds, mergedCount, 1L);
    }
    public ProcessedVisit(Long id, SignificantPlace place, Instant startTime, Instant endTime, String originalVisitIds, Long durationSeconds, Integer mergedCount, Long version) {
        this.id = id;
        this.place = place;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationSeconds = durationSeconds;
        this.originalVisitIds = originalVisitIds;
        this.mergedCount = mergedCount;
        this.version = version;
    }
    
    // Getters and Setters
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
    
    public String getOriginalVisitIds() {
        return originalVisitIds;
    }
    
    public Integer getMergedCount() {
        return mergedCount;
    }

    public Long getVersion() {
        return this.version;
    }

    public ProcessedVisit withId(Long id) {
        return new ProcessedVisit(id, this.place, this.startTime, this.endTime, this.originalVisitIds, this.durationSeconds, this.mergedCount, this.version);
    }

    public ProcessedVisit withVersion(long version) {
        return new ProcessedVisit(this.id, this.place, this.startTime, this.endTime, this.originalVisitIds, this.durationSeconds, this.mergedCount, version);
    }
}
