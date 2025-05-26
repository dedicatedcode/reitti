package com.dedicatedcode.reitti.model;

import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
    
    @Column(nullable = false)
    private Instant firstSeen;
    
    @Column(nullable = false)
    private Instant lastSeen;
    
    @Column(name = "visit_count")
    private Integer visitCount = 0;
    
    @Column(name = "total_duration_seconds")
    private Long totalDurationSeconds = 0L;
    
    @OneToMany(mappedBy = "place", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Visit> visits = new ArrayList<>();

    public SignificantPlace() {}
    public SignificantPlace(User user, String name, String address, Double latitudeCentroid, Double longitudeCentroid, String category, Instant firstSeen, Instant lastSeen) {
        this.user = user;
        this.name = name;
        this.address = address;
        this.latitudeCentroid = latitudeCentroid;
        this.longitudeCentroid = longitudeCentroid;
        this.category = category;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
        this.visitCount = 1;
        this.totalDurationSeconds = 0L;
    }
    
    public SignificantPlace(User user, String name, String address, Double latitudeCentroid, Double longitudeCentroid, Point geom, String category, Instant firstSeen, Instant lastSeen) {
        this.user = user;
        this.name = name;
        this.address = address;
        this.latitudeCentroid = latitudeCentroid;
        this.longitudeCentroid = longitudeCentroid;
        this.geom = geom;
        this.category = category;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
        this.visitCount = 1;
        this.totalDurationSeconds = 0L;
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

    public Instant getFirstSeen() {
        return firstSeen;
    }

    public void setFirstSeen(Instant firstSeen) {
        this.firstSeen = firstSeen;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }

    public List<Visit> getVisits() {
        return visits;
    }

    public void setVisits(List<Visit> visits) {
        this.visits = visits;
    }
    
    public Point getGeom() {
        return geom;
    }
    
    public void setGeom(Point geom) {
        this.geom = geom;
    }
    
    public Integer getVisitCount() {
        return visitCount;
    }
    
    public void setVisitCount(Integer visitCount) {
        this.visitCount = visitCount;
    }
    
    public Long getTotalDurationSeconds() {
        return totalDurationSeconds;
    }
    
    public void setTotalDurationSeconds(Long totalDurationSeconds) {
        this.totalDurationSeconds = totalDurationSeconds;
    }
    
    public void incrementVisitCount() {
        this.visitCount++;
    }
    
    public void addDuration(Long durationSeconds) {
        this.totalDurationSeconds += durationSeconds;
    }
}
