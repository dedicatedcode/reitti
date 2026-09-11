package com.dedicatedcode.reitti.model.geo;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record NoVisitZone(Long id, String name, List<GeoPoint> polygon, Instant createdAt) {

    public NoVisitZone(String name, List<GeoPoint> polygon) {
        this(null, name, polygon, null);
    }

    public NoVisitZone withId(Long id) {
        return new NoVisitZone(id, this.name, this.polygon, this.createdAt);
    }

    public NoVisitZone withPolygon(List<GeoPoint> polygon) {
        return new NoVisitZone(this.id, this.name, polygon, this.createdAt);
    }

    public NoVisitZone withCreatedAt(Instant createdAt) {
        return new NoVisitZone(this.id, this.name, this.polygon, createdAt);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        NoVisitZone that = (NoVisitZone) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "NoVisitZone{" +
                "id=" + id +
                ", name='" + name + '\'' +
                '}';
    }
}
