package com.dedicatedcode.reitti.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "processed_visits")
public class ProcessedVisit {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private final Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private final User user;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "place_id", nullable = false)
    private final SignificantPlace place;
    
    @Column(name = "start_time", nullable = false)
    private final Instant startTime;
    
    @Column(name = "end_time", nullable = false)
    private final Instant endTime;
    
    @Column(name = "duration_seconds", nullable = false)
    private final Long durationSeconds;
    
    @Column(name = "original_visit_ids")
    private final String originalVisitIds; // Comma-separated list of original visit IDs
    
    @Column(name = "merged_count")
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
