package com.dedicatedcode.reitti.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "raw_location_points")
public class RawLocationPoint {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false)
    private Instant timestamp;
    
    @Column(nullable = false)
    private Double latitude;
    
    @Column(nullable = false)
    private Double longitude;
    
    @Column(nullable = false)
    private Double accuracyMeters;
    
    @Column
    private String activityProvided;

    public RawLocationPoint() {
    }
    public RawLocationPoint(User user, Instant timestamp, Double latitude, Double longitude, Double accuracyMeters) {
        this.user = user;
        this.timestamp = timestamp;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyMeters = accuracyMeters;
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

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getAccuracyMeters() {
        return accuracyMeters;
    }

    public void setAccuracyMeters(Double accuracyMeters) {
        this.accuracyMeters = accuracyMeters;
    }

    public String getActivityProvided() {
        return activityProvided;
    }

    public void setActivityProvided(String activityProvided) {
        this.activityProvided = activityProvided;
    }
}
