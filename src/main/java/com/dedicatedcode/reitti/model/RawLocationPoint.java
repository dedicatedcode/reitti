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
    private final Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private final User user;
    
    @Column(nullable = false)
    private final Instant timestamp;
    
    @Column(nullable = false)
    private final Double accuracyMeters;
    
    @Column
    private final String activityProvided;

    @Column(columnDefinition = "geometry(Point,4326)", nullable = false)
    private final Point geom;

    @Column(nullable = false)
    private final boolean processed;

    @Version
    private final Long version;

    public RawLocationPoint() {
        this(null, null, null, null, null, null, false, null);
    }
    
    public RawLocationPoint(User user, Instant timestamp, Point geom, Double accuracyMeters) {
        this(null, user, timestamp, geom, accuracyMeters, null, false, null);
    }
    
    public RawLocationPoint(User user, Instant timestamp, Point geom, Double accuracyMeters, String activityProvided) {
        this(null, user, timestamp, geom, accuracyMeters, activityProvided, false, null);
    }
    
    public RawLocationPoint(Long id, User user, Instant timestamp, Point geom, Double accuracyMeters, String activityProvided, boolean processed, Long version) {
        this.id = id;
        this.user = user;
        this.timestamp = timestamp;
        this.geom = geom;
        this.accuracyMeters = accuracyMeters;
        this.activityProvided = activityProvided;
        this.processed = processed;
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Instant getTimestamp() {
        return timestamp;
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

    public String getActivityProvided() {
        return activityProvided;
    }

    public Point getGeom() {
        return geom;
    }

    public boolean isProcessed() {
        return processed;
    }

    public RawLocationPoint markProcessed() {
        return new RawLocationPoint(this.id, this.user, this.timestamp, this.geom, this.accuracyMeters, this.activityProvided, true, this.version);
    }

    public Long getVersion() {
        return version;
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
