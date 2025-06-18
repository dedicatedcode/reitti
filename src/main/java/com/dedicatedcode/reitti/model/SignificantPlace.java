package com.dedicatedcode.reitti.model;

import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SignificantPlace {
    
    private Long id;
    private User user;
    private String name;
    private String address;
    private Double latitudeCentroid;
    private Double longitudeCentroid;
    private Point geom;
    private String category;
    private boolean geocoded = false;
    private Long version;

    public SignificantPlace() {}

    public SignificantPlace(User user,
                            String name,
                            String address,
                            Double latitudeCentroid,
                            Double longitudeCentroid,
                            Point geom,
                            String category) {
        this.user = user;
        this.name = name;
        this.address = address;
        this.latitudeCentroid = latitudeCentroid;
        this.longitudeCentroid = longitudeCentroid;
        this.geom = geom;
        this.category = category;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Double getLatitudeCentroid() {
        return latitudeCentroid;
    }

    public void setLatitudeCentroid(Double latitudeCentroid) {
        this.latitudeCentroid = latitudeCentroid;
    }

    public Double getLongitudeCentroid() {
        return longitudeCentroid;
    }

    public void setLongitudeCentroid(Double longitudeCentroid) {
        this.longitudeCentroid = longitudeCentroid;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Point getGeom() {
        return geom;
    }
    
    public void setGeom(Point geom) {
        this.geom = geom;
    }

    public boolean isGeocoded() { 
        return geocoded; 
    }

    public void setGeocoded(boolean geocoded) { 
        this.geocoded = geocoded; 
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
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
