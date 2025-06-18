package com.dedicatedcode.reitti.model;

import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.Objects;

public class RawLocationPoint {
    
    private final Long id;
    
    private final User user;
    
    private final Instant timestamp;
    
    private final Double accuracyMeters;
    
    private final String activityProvided;

    private final Point geom;

    private final boolean processed;

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
