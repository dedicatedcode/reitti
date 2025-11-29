package com.dedicatedcode.reitti.model.geo;

import java.time.Instant;
import java.util.Objects;

public class RawLocationPoint {
    
    private final Long id;
    
    private final Instant timestamp;
    
    private final Double accuracyMeters;
    
    private final Double elevationMeters;
    
    private final GeoPoint geom;

    private final boolean processed;
    
    private final boolean synthetic;
    
    private final boolean ignored;

    private final Long version;

    public RawLocationPoint() {
        this(null, null, null, null, null, false, false, false, null);
    }
    
    public RawLocationPoint(Instant timestamp, GeoPoint geom, Double accuracyMeters) {
        this(null, timestamp, geom, accuracyMeters, null, false, false, false, null);
    }
    
    public RawLocationPoint(Instant timestamp, GeoPoint geom, Double accuracyMeters, Double elevationMeters) {
        this(null, timestamp, geom, accuracyMeters, elevationMeters, false, false, false, null);
    }
    
    public RawLocationPoint(Long id, Instant timestamp, GeoPoint geom, Double accuracyMeters, Double elevationMeters, boolean processed, Long version) {
        this(id, timestamp, geom, accuracyMeters, elevationMeters, processed, false, false, version);
    }
    
    public RawLocationPoint(Long id, Instant timestamp, GeoPoint geom, Double accuracyMeters, Double elevationMeters, boolean processed, boolean synthetic, boolean ignored, Long version) {
        this.id = id;
        this.timestamp = timestamp;
        this.geom = geom;
        this.accuracyMeters = accuracyMeters;
        this.elevationMeters = elevationMeters;
        this.processed = processed;
        this.synthetic = synthetic;
        this.ignored = ignored;
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Double getLatitude() {
        return this.geom.latitude();
    }

    public Double getLongitude() {
        return this.geom.longitude();
    }

    public Double getAccuracyMeters() {
        return accuracyMeters;
    }

    public Double getElevationMeters() {
        return elevationMeters;
    }

    public GeoPoint getGeom() {
        return geom;
    }

    public boolean isProcessed() {
        return processed;
    }
    
    public boolean isSynthetic() {
        return synthetic;
    }
    
    public boolean isIgnored() {
        return ignored;
    }

    public RawLocationPoint markProcessed() {
        return new RawLocationPoint(this.id, this.timestamp, this.geom, this.accuracyMeters, this.elevationMeters, true, this.synthetic, this.ignored, this.version);
    }
    
    public RawLocationPoint markAsSynthetic() {
        return new RawLocationPoint(this.id, this.timestamp, this.geom, this.accuracyMeters, this.elevationMeters, this.processed, true, this.ignored, this.version);
    }
    
    public RawLocationPoint markAsIgnored() {
        return new RawLocationPoint(this.id, this.timestamp, this.geom, this.accuracyMeters, this.elevationMeters, this.processed, this.synthetic, true, this.version);
    }

    public RawLocationPoint withId(Long id) {
        return new RawLocationPoint(id, timestamp, geom, accuracyMeters, elevationMeters, processed, synthetic, ignored, version);
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
