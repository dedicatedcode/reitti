package com.dedicatedcode.reitti.model;

import jakarta.persistence.*;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.Objects;

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
    private Double accuracyMeters;
    
    @Column
    private String activityProvided;

    @Column(columnDefinition = "geometry(Point,4326)", nullable = false)
    private Point geom;

    @Column(nullable = false)
    private boolean processed;

    public RawLocationPoint() {
    }
    public RawLocationPoint(User user, Instant timestamp, Point geom, Double accuracyMeters) {
        this.user = user;
        this.timestamp = timestamp;
        this.accuracyMeters = accuracyMeters;
        this.geom = geom;
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
        return this.geom.getY();
    }

    public Double getLongitude() {
        return this.geom.getCoordinate().getX();
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

    public Point getGeom() {
        return geom;
    }

    public void setGeom(Point geom) {
        this.geom = geom;
    }

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }

    public void markProcessed() {
        this.processed = true;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        RawLocationPoint that = (RawLocationPoint) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
