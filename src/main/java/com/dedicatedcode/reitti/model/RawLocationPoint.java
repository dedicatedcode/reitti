package com.dedicatedcode.reitti.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "raw_location_points")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RawLocationPoint {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false)
    private Instant timestamp;
    
    @Column(nullable = false)
    private Double latitude;
    
    @Column(nullable = false)
    private Double longitude;
    
    @Column(nullable = false)
    private Double accuracyMeters;
    
    @Column
    private String activityProvided;
    
    // This would be a PostGIS geometry point in a real implementation
    // For now, we'll use latitude and longitude directly
}
