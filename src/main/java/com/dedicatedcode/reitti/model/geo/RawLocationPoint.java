package com.dedicatedcode.reitti.model.geo;

import java.time.Instant;
import java.util.Objects;

public class RawLocationPoint {
    
    private final Long id;
    private final Long sourceId;
    private final Instant timestamp;
    private final Double accuracyMeters;
    private final Double elevationMeters;
    private final GeoPoint geom;
    private final boolean processed;
    private final boolean synthetic;

    private final Long version;

    public RawLocationPoint(Instant timestamp, GeoPoint geom, Double accuracyMeters) {
        this(null, null, timestamp, geom, accuracyMeters, null, false, false, null);
    }
    
    public RawLocationPoint(Instant timestamp, GeoPoint geom, Double accuracyMeters, Double elevationMeters) {
        this(null, null, timestamp, geom, accuracyMeters, elevationMeters, false, false, null);
    }
    
    public RawLocationPoint(Long id, Instant timestamp, GeoPoint geom, Double accuracyMeters, Double elevationMeters, boolean processed, Long version) {
        this(id, null, timestamp, geom, accuracyMeters, elevationMeters, processed, false, version);
    }
    
    public RawLocationPoint(Long id, Long sourceId, Instant timestamp, GeoPoint geom, Double accuracyMeters, Double elevationMeters, boolean processed, boolean synthetic, Long version) {
        this.id = id;
        this.sourceId = sourceId;
        this.timestamp = timestamp;
        this.geom = geom;
        this.accuracyMeters = accuracyMeters;
        this.elevationMeters = elevationMeters;
        this.processed = processed;
        this.synthetic = synthetic;
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public Long getSourceId() {
        return sourceId;
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

    public RawLocationPoint markProcessed() {
        return new RawLocationPoint(id, sourceId, timestamp, geom, accuracyMeters, elevationMeters, true, synthetic, version);
    }
    
    public RawLocationPoint markAsSynthetic() {
        return new RawLocationPoint(id, sourceId, timestamp, geom, accuracyMeters, elevationMeters, processed, true, version);
    }
    
    public RawLocationPoint withId(Long id) {
        return new RawLocationPoint(id, sourceId, timestamp, geom, accuracyMeters, elevationMeters, processed, synthetic, version);
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
