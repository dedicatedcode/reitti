package com.dedicatedcode.reitti.model;

import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "trips")
public class Trip {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "start_place_id")
    private SignificantPlace startPlace;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "end_place_id")
    private SignificantPlace endPlace;
    
    @Column(nullable = false)
    private Instant startTime;
    
    @Column(nullable = false)
    private Instant endTime;
    
    @Column(nullable = false)
    private Long durationSeconds;
    
    @Column
    private Double estimatedDistanceMeters;
    
    @Column(name = "travelled_distance_meters")
    private Double travelledDistanceMeters;
    
    @Column
    private String transportModeInferred;

    public Trip() {}

    public Trip(User user, SignificantPlace startPlace, SignificantPlace endPlace, Instant startTime, Instant endTime, Double estimatedDistanceMeters, String transportModeInferred) {
        this.user = user;
        this.startPlace = startPlace;
        this.endPlace = endPlace;
        this.startTime = startTime;
        this.endTime = endTime;
        this.estimatedDistanceMeters = estimatedDistanceMeters;
        this.transportModeInferred = transportModeInferred;
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

    @PrePersist
    @PreUpdate
    private void calculateDuration() {
        if (startTime != null && endTime != null) {
            durationSeconds = Duration.between(startTime, endTime).getSeconds();
        }
    }


}
