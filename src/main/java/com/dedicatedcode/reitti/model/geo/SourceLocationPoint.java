package com.dedicatedcode.reitti.model.geo;

import java.time.Instant;
import java.util.Objects;

public class SourceLocationPoint {

    private final Long id;
    private final Instant timestamp;
    private final Double accuracyMeters;
    private final Double elevationMeters;
    private final GeoPoint geom;
    private final boolean invalid;
    private final boolean ignored;

    public SourceLocationPoint(Instant timestamp, GeoPoint geom, Double accuracyMeters) {
        this(null, timestamp, geom, accuracyMeters, null, false, false);
    }

    public SourceLocationPoint(Instant timestamp, GeoPoint geom, Double accuracyMeters, Double elevationMeters) {
        this(null, timestamp, geom, accuracyMeters, elevationMeters, false, false);
    }

    public SourceLocationPoint(Long id, Instant timestamp, GeoPoint geom, Double accuracyMeters, Double elevationMeters) {
        this(id, timestamp, geom, accuracyMeters, elevationMeters, false, false);
    }

    public SourceLocationPoint(Long id, Instant timestamp, GeoPoint geom, Double accuracyMeters, Double elevationMeters, boolean ignored, boolean invalid) {
        this.id = id;
        this.timestamp = timestamp;
        this.geom = geom;
        this.accuracyMeters = accuracyMeters;
        this.elevationMeters = elevationMeters;
        this.invalid = invalid;
        this.ignored = ignored;
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

    public boolean isInvalid() {
        return invalid;
    }

    public boolean isIgnored() {
        return ignored;
    }

    public SourceLocationPoint markAsIgnored() {
        return new SourceLocationPoint(id, timestamp, geom, accuracyMeters, elevationMeters, true, invalid);
    }

    public SourceLocationPoint markAsInvalid() {
        return new SourceLocationPoint(id, timestamp, geom, accuracyMeters, elevationMeters, true, invalid);
    }

    public SourceLocationPoint withId(Long id) {
        return new SourceLocationPoint(id, timestamp, geom, accuracyMeters, elevationMeters, ignored, invalid);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SourceLocationPoint that = (SourceLocationPoint) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

}
