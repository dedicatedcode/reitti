package com.dedicatedcode.reitti.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "trips")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Trip {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "start_place_id")
    private SignificantPlace startPlace;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "end_place_id")
    private SignificantPlace endPlace;
    
    @Column(nullable = false)
    private Instant startTime;
    
    @Column(nullable = false)
    private Instant endTime;
    
    @Column(nullable = false)
    private Long durationSeconds;
    
    @Column
    private Double estimatedDistanceMeters;
    
    @Column
    private String transportModeInferred;
    
    // This would be a PostGIS linestring in a real implementation
    // For now, we'll omit the path geometry
    
    @PrePersist
    @PreUpdate
    private void calculateDuration() {
        if (startTime != null && endTime != null) {
            durationSeconds = Duration.between(startTime, endTime).getSeconds();
        }
    }
}
