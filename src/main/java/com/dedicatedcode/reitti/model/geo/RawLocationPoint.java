package com.dedicatedcode.reitti.model.geo;

import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.Objects;

public class RawLocationPoint {
    
    private final Long id;
    
    private final Instant timestamp;
    
    private final Double accuracyMeters;
    
    private final Point geom;

    private final boolean processed;

    private final Long version;

    public RawLocationPoint() {
        this(null, null, null, null, false, null);
    }
    
    public RawLocationPoint(Instant timestamp, Point geom, Double accuracyMeters) {
        this(null, timestamp, geom, accuracyMeters, false, null);
    }
    
    public RawLocationPoint(Long id, Instant timestamp, Point geom, Double accuracyMeters, boolean processed, Long version) {
        this.id = id;
        this.timestamp = timestamp;

        this.geom = geom;
        this.accuracyMeters = accuracyMeters;
        this.processed = processed;
        this.version = version;
    }

    public Long getId() {
        return id;
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

    public Point getGeom() {
        return geom;
    }

    public boolean isProcessed() {
        return processed;
    }

    public RawLocationPoint markProcessed() {
        return new RawLocationPoint(this.id, this.timestamp, this.geom, this.accuracyMeters, true, this.version);
    }

    public RawLocationPoint withId(Long id) {
        return new RawLocationPoint(id, timestamp, geom, accuracyMeters, processed, version);
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
