package com.dedicatedcode.reitti.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "processed_visits")
public class ProcessedVisit {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "place_id", nullable = false)
    private SignificantPlace place;
    
    @Column(name = "start_time", nullable = false)
    private Instant startTime;
    
    @Column(name = "end_time", nullable = false)
    private Instant endTime;
    
    @Column(name = "duration_seconds", nullable = false)
    private Long durationSeconds;
    
    @Column(name = "original_visit_ids")
    private String originalVisitIds; // Comma-separated list of original visit IDs
    
    @Column(name = "merged_count")
    private Integer mergedCount;
    
    @PrePersist
    @PreUpdate
    private void calculateDuration() {
        if (startTime != null && endTime != null) {
            durationSeconds = endTime.getEpochSecond() - startTime.getEpochSecond();
        }
    }
    
    // Constructors
    public ProcessedVisit() {
        this.mergedCount = 1;
    }
    
    public ProcessedVisit(User user, SignificantPlace place, Instant startTime, Instant endTime) {
        this.user = user;
        this.place = place;
        this.startTime = startTime;
        this.endTime = endTime;
        this.mergedCount = 1;
    }
    
    // Getters and Setters
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
    
    public SignificantPlace getPlace() {
        return place;
    }
    
    public void setPlace(SignificantPlace place) {
        this.place = place;
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
    
    public String getOriginalVisitIds() {
        return originalVisitIds;
    }
    
    public void setOriginalVisitIds(String originalVisitIds) {
        this.originalVisitIds = originalVisitIds;
    }
    
    public Integer getMergedCount() {
        return mergedCount;
    }
    
    public void setMergedCount(Integer mergedCount) {
        this.mergedCount = mergedCount;
    }
    
    public void incrementMergedCount() {
        this.mergedCount++;
    }
    
    public void addOriginalVisitId(Long visitId) {
        if (this.originalVisitIds == null || this.originalVisitIds.isEmpty()) {
            this.originalVisitIds = visitId.toString();
        } else {
            this.originalVisitIds += "," + visitId;
        }
    }
}
