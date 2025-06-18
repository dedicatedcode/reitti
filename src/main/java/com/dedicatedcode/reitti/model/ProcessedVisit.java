package com.dedicatedcode.reitti.model;

import java.time.Instant;

public class ProcessedVisit {
    
    private final Long id;
    
    private final User user;
    
    private final SignificantPlace place;
    
    private final Instant startTime;
    
    private final Instant endTime;
    
    private final Long durationSeconds;
    
    private final String originalVisitIds; // Comma-separated list of original visit IDs
    
    private final Integer mergedCount;
    
    // Constructors
    public ProcessedVisit() {
        this(null, null, null, null, null, null, 1);
    }
    
    public ProcessedVisit(User user, SignificantPlace place, Instant startTime, Instant endTime) {
        this(null, user, place, startTime, endTime, null, 1);
    }
    
    public ProcessedVisit(Long id, User user, SignificantPlace place, Instant startTime, Instant endTime, String originalVisitIds, Integer mergedCount) {
        this.id = id;
        this.user = user;
        this.place = place;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationSeconds = (startTime != null && endTime != null) ? 
            endTime.getEpochSecond() - startTime.getEpochSecond() : null;
        this.originalVisitIds = originalVisitIds;
        this.mergedCount = mergedCount != null ? mergedCount : 1;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public User getUser() {
        return user;
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
    
    // Wither methods
    public ProcessedVisit withIncrementedMergedCount() {
        return new ProcessedVisit(this.id, this.user, this.place, this.startTime, this.endTime, this.originalVisitIds, this.mergedCount + 1);
    }
    
    public ProcessedVisit withAddedOriginalVisitId(Long visitId) {
        String newIds = (this.originalVisitIds == null || this.originalVisitIds.isEmpty()) ? 
            visitId.toString() : this.originalVisitIds + "," + visitId;
        return new ProcessedVisit(this.id, this.user, this.place, this.startTime, this.endTime, newIds, this.mergedCount);
    }
}
