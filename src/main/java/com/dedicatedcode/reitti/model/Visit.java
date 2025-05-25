package com.dedicatedcode.reitti.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "visits")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Visit {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private SignificantPlace place;
    
    @Column(nullable = false)
    private Instant startTime;
    
    @Column(nullable = false)
    private Instant endTime;
    
    @Column(nullable = false)
    private Long durationSeconds;
    
    @PrePersist
    @PreUpdate
    private void calculateDuration() {
        if (startTime != null && endTime != null) {
            durationSeconds = Duration.between(startTime, endTime).getSeconds();
        }
    }
}
