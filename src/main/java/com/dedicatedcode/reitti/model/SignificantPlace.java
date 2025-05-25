package com.dedicatedcode.reitti.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "significant_places")
@Data
@NoArgsConstructor
@AllArgsConstructor
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
    
    @Column
    private String category;
    
    @Column(nullable = false)
    private Instant firstSeen;
    
    @Column(nullable = false)
    private Instant lastSeen;
    
    @OneToMany(mappedBy = "place", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Visit> visits = new ArrayList<>();
    
    // This would be a PostGIS geometry polygon or point in a real implementation
    // For now, we'll use latitude and longitude centroid directly
}
