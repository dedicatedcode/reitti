package com.dedicatedcode.reitti.model;

import jakarta.persistence.*;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "significant_places")
public class SignificantPlace {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column
    private String name;
    
    @Column
    private String address;
    
    @Column(nullable = false)
    private Double latitudeCentroid;
    
    @Column(nullable = false)
    private Double longitudeCentroid;
    
    @Column(columnDefinition = "geometry(Point,4326)")
    private Point geom;
    
    @Column
    private String category;

    @OneToMany(mappedBy = "place", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProcessedVisit> visits = new ArrayList<>();

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

    public List<ProcessedVisit> getVisits() {
        return visits;
    }

    public void setVisits(List<ProcessedVisit> visits) {
        this.visits = visits;
    }
    
    public Point getGeom() {
        return geom;
    }
    
    public void setGeom(Point geom) {
        this.geom = geom;
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
