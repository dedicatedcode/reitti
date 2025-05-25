package com.dedicatedcode.reitti.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Location {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private double latitude;
    private double longitude;
    private LocalDateTime timestamp;
    private String description;
    
    // Constructor without id for creating new locations
    public Location(double latitude, double longitude, LocalDateTime timestamp, String description) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.description = description;
    }
}
