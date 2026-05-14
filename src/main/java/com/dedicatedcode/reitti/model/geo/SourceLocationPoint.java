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
    private final Status status;

    public SourceLocationPoint(Instant timestamp, GeoPoint geom, Double accuracyMeters) {
        this(null, timestamp, geom, accuracyMeters, null, Status.VALID, false);
    }

    public SourceLocationPoint(Instant timestamp, GeoPoint geom, Double accuracyMeters, Double elevationMeters) {
        this(null, timestamp, geom, accuracyMeters, elevationMeters, Status.VALID, false);
    }

    public SourceLocationPoint(Long id, Instant timestamp, GeoPoint geom, Double accuracyMeters, Double elevationMeters) {
        this(id, timestamp, geom, accuracyMeters, elevationMeters, Status.VALID, false);
    }

    public SourceLocationPoint(Long id, Instant timestamp, GeoPoint geom, Double accuracyMeters, Double elevationMeters, Status status, boolean invalid) {
        this.id = id;
        this.timestamp = timestamp;
        this.geom = geom;
        this.accuracyMeters = accuracyMeters;
        this.elevationMeters = elevationMeters;
        this.invalid = invalid;
        this.status = status;
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

    public Status getStatus() {
        return status;
    }

    public SourceLocationPoint markAsIgnored() {
        return new SourceLocationPoint(id, timestamp, geom, accuracyMeters, elevationMeters, Status.IGNORED_BY_SYSTEM, invalid);
    }

    public SourceLocationPoint markAsInvalid() {
        return new SourceLocationPoint(id, timestamp, geom, accuracyMeters, elevationMeters, status, true);
    }

    public SourceLocationPoint withId(Long id) {
        return new SourceLocationPoint(id, timestamp, geom, accuracyMeters, elevationMeters, status, invalid);
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

    public enum Status {
        VALID(0L),
        IGNORED_BY_USER(1L),
        IGNORED_BY_SYSTEM(2L);

        private final long dbValue;

        Status(long dbValue) {
            this.dbValue = dbValue;
        }

        public static Status fromDbValue(long dbValue) {
            for (Status status : Status.values()) {
                if (status.dbValue == dbValue) {
                    return status;
                }
            }
            return null;
        }

        public long getDbValue() {
            return dbValue;
        }
    }
}
