package com.dedicatedcode.reitti.model.geo;

import java.time.Instant;
import java.util.Objects;

public record SuppressedVisit(Long id, Long placeId, Double latitudeCentroid, Double longitudeCentroid,
                              Instant startTime, Instant endTime, Instant createdAt) {

    public SuppressedVisit(Long placeId, Double latitudeCentroid, Double longitudeCentroid, Instant startTime, Instant endTime) {
        this(null, placeId, latitudeCentroid, longitudeCentroid, startTime, endTime, null);
    }

    public SuppressedVisit withId(Long id) {
        return new SuppressedVisit(id, this.placeId, this.latitudeCentroid, this.longitudeCentroid, this.startTime, this.endTime, this.createdAt);
    }

    public SuppressedVisit withCreatedAt(Instant createdAt) {
        return new SuppressedVisit(this.id, this.placeId, this.latitudeCentroid, this.longitudeCentroid, this.startTime, this.endTime, createdAt);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SuppressedVisit that = (SuppressedVisit) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "SuppressedVisit{" +
                "id=" + id +
                ", placeId=" + placeId +
                ", latitudeCentroid=" + latitudeCentroid +
                ", longitudeCentroid=" + longitudeCentroid +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                '}';
    }
}
