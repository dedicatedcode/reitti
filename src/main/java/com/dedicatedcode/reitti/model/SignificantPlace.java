package com.dedicatedcode.reitti.model;

import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SignificantPlace {
    
    private final Long id;
    private final User user;
    private final String name;
    private final String address;
    private final Double latitudeCentroid;
    private final Double longitudeCentroid;
    private final Point geom;
    private final String category;
    private final boolean geocoded;
    private final Long version;

    public SignificantPlace() {
        this(null, null, null, null, null, null, null, null, false, null);
    }

    public SignificantPlace(User user,
                            String name,
                            String address,
                            Double latitudeCentroid,
                            Double longitudeCentroid,
                            Point geom,
                            String category) {
        this(null, user, name, address, latitudeCentroid, longitudeCentroid, geom, category, false, null);
    }
    
    public SignificantPlace(Long id, User user, String name, String address, Double latitudeCentroid, Double longitudeCentroid, Point geom, String category, boolean geocoded, Long version) {
        this.id = id;
        this.user = user;
        this.name = name;
        this.address = address;
        this.latitudeCentroid = latitudeCentroid;
        this.longitudeCentroid = longitudeCentroid;
        this.geom = geom;
        this.category = category;
        this.geocoded = geocoded;
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public Double getLatitudeCentroid() {
        return latitudeCentroid;
    }

    public Double getLongitudeCentroid() {
        return longitudeCentroid;
    }

    public String getCategory() {
        return category;
    }

    public Point getGeom() {
        return geom;
    }

    public boolean isGeocoded() { 
        return geocoded; 
    }

    public Long getVersion() {
        return version;
    }
    
    // Wither methods
    public SignificantPlace withGeocoded(boolean geocoded) {
        return new SignificantPlace(this.id, this.user, this.name, this.address, this.latitudeCentroid, this.longitudeCentroid, this.geom, this.category, geocoded, this.version);
    }

    public SignificantPlace withName(String name) {
        return new SignificantPlace(this.id, this.user, name, this.address, this.latitudeCentroid, this.longitudeCentroid, this.geom, this.category, this.geocoded, this.version);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SignificantPlace that = (SignificantPlace) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "SignificantPlace{" +
                "id=" + id +
                ", user=" + user +
                ", name='" + name + '\'' +
                ", geom=" + geom +
                '}';
    }
}
