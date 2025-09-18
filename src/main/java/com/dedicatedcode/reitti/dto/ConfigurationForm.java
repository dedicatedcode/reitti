package com.dedicatedcode.reitti.dto;

import com.dedicatedcode.reitti.model.processing.Configuration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public class ConfigurationForm {
    private Long id;
    private String mode = "simple";
    
    // Simple mode
    private Integer sensitivityLevel = 3; // 1-5 scale
    
    // Advanced mode - all Configuration fields
    private Long searchDistanceInMeters;
    private Long minimumAdjacentPoints;
    private Long minimumStayTimeInSeconds;
    private Long maxMergeTimeBetweenSameStayPoints;
    private Long searchDurationInHours;
    private Long maxMergeTimeBetweenSameVisits;
    private Long minDistanceBetweenVisits;
    
    private LocalDate validSince;
    
    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    
    public Integer getSensitivityLevel() { return sensitivityLevel; }
    public void setSensitivityLevel(Integer sensitivityLevel) { this.sensitivityLevel = sensitivityLevel; }
    
    public Long getSearchDistanceInMeters() { return searchDistanceInMeters; }
    public void setSearchDistanceInMeters(Long searchDistanceInMeters) { this.searchDistanceInMeters = searchDistanceInMeters; }
    
    public Long getMinimumAdjacentPoints() { return minimumAdjacentPoints; }
    public void setMinimumAdjacentPoints(Long minimumAdjacentPoints) { this.minimumAdjacentPoints = minimumAdjacentPoints; }
    
    public Long getMinimumStayTimeInSeconds() { return minimumStayTimeInSeconds; }
    public void setMinimumStayTimeInSeconds(Long minimumStayTimeInSeconds) { this.minimumStayTimeInSeconds = minimumStayTimeInSeconds; }
    
    public Long getMaxMergeTimeBetweenSameStayPoints() { return maxMergeTimeBetweenSameStayPoints; }
    public void setMaxMergeTimeBetweenSameStayPoints(Long maxMergeTimeBetweenSameStayPoints) { this.maxMergeTimeBetweenSameStayPoints = maxMergeTimeBetweenSameStayPoints; }
    
    public Long getSearchDurationInHours() { return searchDurationInHours; }
    public void setSearchDurationInHours(Long searchDurationInHours) { this.searchDurationInHours = searchDurationInHours; }
    
    public Long getMaxMergeTimeBetweenSameVisits() { return maxMergeTimeBetweenSameVisits; }
    public void setMaxMergeTimeBetweenSameVisits(Long maxMergeTimeBetweenSameVisits) { this.maxMergeTimeBetweenSameVisits = maxMergeTimeBetweenSameVisits; }
    
    public Long getMinDistanceBetweenVisits() { return minDistanceBetweenVisits; }
    public void setMinDistanceBetweenVisits(Long minDistanceBetweenVisits) { this.minDistanceBetweenVisits = minDistanceBetweenVisits; }
    
    public LocalDate getValidSince() { return validSince; }
    public void setValidSince(LocalDate validSince) { this.validSince = validSince; }
    
    // Convert from Configuration
    public static ConfigurationForm fromConfiguration(Configuration config) {
        ConfigurationForm form = new ConfigurationForm();
        form.setId(config.getId());
        
        // Set advanced mode values
        form.setSearchDistanceInMeters(config.getVisitDetection().getSearchDistanceInMeters());
        form.setMinimumAdjacentPoints(config.getVisitDetection().getMinimumAdjacentPoints());
        form.setMinimumStayTimeInSeconds(config.getVisitDetection().getMinimumStayTimeInSeconds());
        form.setMaxMergeTimeBetweenSameStayPoints(config.getVisitDetection().getMaxMergeTimeBetweenSameStayPoints());
        form.setSearchDurationInHours(config.getVisitMerging().getSearchDurationInHours());
        form.setMaxMergeTimeBetweenSameVisits(config.getVisitMerging().getMaxMergeTimeBetweenSameVisits());
        form.setMinDistanceBetweenVisits(config.getVisitMerging().getMinDistanceBetweenVisits());
        
        // Convert to simple mode sensitivity level (1-5 scale based on stay time)
        long stayTime = config.getVisitDetection().getMinimumStayTimeInSeconds();
        if (stayTime <= 180) form.setSensitivityLevel(5); // High sensitivity
        else if (stayTime <= 300) form.setSensitivityLevel(4);
        else if (stayTime <= 600) form.setSensitivityLevel(3); // Medium
        else if (stayTime <= 1200) form.setSensitivityLevel(2);
        else form.setSensitivityLevel(1); // Low sensitivity
        
        if (config.getValidSince() != null) {
            form.setValidSince(LocalDate.ofInstant(config.getValidSince(), ZoneOffset.UTC));
        }
        
        return form;
    }
    
    // Convert to Configuration
    public Configuration toConfiguration() {
        Configuration.VisitDetection visitDetection;
        Configuration.VisitMerging visitMerging;
        
        if ("simple".equals(mode)) {
            // Map sensitivity level to parameters
            visitDetection = mapSensitivityToVisitDetection(sensitivityLevel);
            visitMerging = mapSensitivityToVisitMerging(sensitivityLevel);
        } else {
            // Use advanced mode values
            visitDetection = new Configuration.VisitDetection(
                searchDistanceInMeters,
                minimumAdjacentPoints,
                minimumStayTimeInSeconds,
                maxMergeTimeBetweenSameStayPoints
            );
            visitMerging = new Configuration.VisitMerging(
                searchDurationInHours,
                maxMergeTimeBetweenSameVisits,
                minDistanceBetweenVisits
            );
        }
        
        Instant validSinceInstant = validSince != null ? validSince.atStartOfDay().toInstant(ZoneOffset.UTC) : null;
        
        return new Configuration(getId(), visitDetection, visitMerging, validSinceInstant);
    }
    
    private Configuration.VisitDetection mapSensitivityToVisitDetection(int level) {
        return switch (level) {
            case 1 -> new Configuration.VisitDetection(300, 5, 1800, 7200); // Low sensitivity
            case 2 -> new Configuration.VisitDetection(200, 4, 1200, 5400);
            case 3 -> new Configuration.VisitDetection(150, 3, 600, 3600);  // Medium
            case 4 -> new Configuration.VisitDetection(100, 3, 300, 1800);
            case 5 -> new Configuration.VisitDetection(75, 2, 180, 900);    // High sensitivity
            default -> new Configuration.VisitDetection(150, 3, 600, 3600); // Default to medium
        };
    }
    
    private Configuration.VisitMerging mapSensitivityToVisitMerging(int level) {
        return switch (level) {
            case 1 -> new Configuration.VisitMerging(72, 14400, 500); // Low sensitivity
            case 2 -> new Configuration.VisitMerging(48, 10800, 300);
            case 3 -> new Configuration.VisitMerging(24, 7200, 200);  // Medium
            case 4 -> new Configuration.VisitMerging(12, 3600, 150);
            case 5 -> new Configuration.VisitMerging(6, 1800, 100);   // High sensitivity
            default -> new Configuration.VisitMerging(24, 7200, 200); // Default to medium
        };
    }
}
