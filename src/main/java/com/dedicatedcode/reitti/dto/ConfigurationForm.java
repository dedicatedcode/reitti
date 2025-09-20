package com.dedicatedcode.reitti.dto;

import com.dedicatedcode.reitti.model.processing.Configuration;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.*;

public class ConfigurationForm {
    private Long id;
    private String mode = "simple";
    
    // Simple mode
    private Integer sensitivityLevel = 3; // 1-5 scale
    
    // Advanced mode - all Configuration fields
    private Long searchDistanceInMeters;
    private Integer minimumAdjacentPoints;
    private Long minimumStayTimeInSeconds;
    private Long maxMergeTimeBetweenSameStayPoints;
    private Long searchDurationInHours;
    private Long maxMergeTimeBetweenSameVisits;
    private Long minDistanceBetweenVisits;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
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
    
    public Integer getMinimumAdjacentPoints() { return minimumAdjacentPoints; }
    public void setMinimumAdjacentPoints(Integer minimumAdjacentPoints) { this.minimumAdjacentPoints = minimumAdjacentPoints; }
    
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
    public static ConfigurationForm fromConfiguration(Configuration config, ZoneId zoneId) {
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
        
        // Check if configuration matches any predefined sensitivity level
        Integer matchingSensitivityLevel = findMatchingSensitivityLevel(config);
        if (matchingSensitivityLevel != null) {
            form.setSensitivityLevel(matchingSensitivityLevel);
            form.setMode("simple");
        } else {
            // Configuration doesn't match any preset, use advanced mode
            form.setSensitivityLevel(3); // Default fallback
            form.setMode("advanced");
        }
        
        if (config.getValidSince() != null) {
            form.setValidSince(LocalDate.ofInstant(config.getValidSince(), zoneId));
        }
        
        return form;
    }
    
    private static Integer findMatchingSensitivityLevel(Configuration config) {
        // Check each sensitivity level to see if it matches the configuration
        for (int level = 1; level <= 5; level++) {
            Configuration.VisitDetection expectedDetection = mapSensitivityToVisitDetection(level);
            Configuration.VisitMerging expectedMerging = mapSensitivityToVisitMerging(level);
            
            if (configurationMatches(config, expectedDetection, expectedMerging)) {
                return level;
            }
        }
        return null; // No match found
    }
    
    private static boolean configurationMatches(Configuration config, 
                                              Configuration.VisitDetection expectedDetection, 
                                              Configuration.VisitMerging expectedMerging) {
        Configuration.VisitDetection actualDetection = config.getVisitDetection();
        Configuration.VisitMerging actualMerging = config.getVisitMerging();
        
        return actualDetection.getSearchDistanceInMeters() == expectedDetection.getSearchDistanceInMeters() &&
               actualDetection.getMinimumAdjacentPoints() == expectedDetection.getMinimumAdjacentPoints() &&
               actualDetection.getMinimumStayTimeInSeconds() == expectedDetection.getMinimumStayTimeInSeconds() &&
               actualDetection.getMaxMergeTimeBetweenSameStayPoints() == expectedDetection.getMaxMergeTimeBetweenSameStayPoints() &&
               actualMerging.getSearchDurationInHours() == expectedMerging.getSearchDurationInHours() &&
               actualMerging.getMaxMergeTimeBetweenSameVisits() == expectedMerging.getMaxMergeTimeBetweenSameVisits() &&
               actualMerging.getMinDistanceBetweenVisits() == expectedMerging.getMinDistanceBetweenVisits();
    }
    
    private static Configuration.VisitDetection mapSensitivityToVisitDetection(int level) {
        return switch (level) {
            case 1 -> new Configuration.VisitDetection(200, 8, 600, 600);   // Low sensitivity
            case 2 -> new Configuration.VisitDetection(150, 6, 450, 450);   
            case 3 -> new Configuration.VisitDetection(100, 5, 300, 300);   // Medium (baseline)
            case 4 -> new Configuration.VisitDetection(75, 4, 225, 225);    
            case 5 -> new Configuration.VisitDetection(50, 3, 150, 150);    // High sensitivity
            default -> throw new IllegalArgumentException("Unhandled level [" + level + "] detected!");
        };
    }
    
    private static Configuration.VisitMerging mapSensitivityToVisitMerging(int level) {
        return switch (level) {
            case 1 -> new Configuration.VisitMerging(96, 600, 400);   // Low sensitivity
            case 2 -> new Configuration.VisitMerging(72, 450, 300);   
            case 3 -> new Configuration.VisitMerging(48, 300, 200);   // Medium (baseline)
            case 4 -> new Configuration.VisitMerging(24, 225, 150);   
            case 5 -> new Configuration.VisitMerging(12, 150, 100);   // High sensitivity
            default -> throw new IllegalArgumentException("Unhandled level [" + level + "] detected!");
        };
    }
    
    // Convert to Configuration
    public Configuration toConfiguration(ZoneId timezone) {
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
        
        Instant validSinceInstant = validSince != null ? ZonedDateTime.of(validSince.atStartOfDay(), timezone).toInstant() : null;
        
        return new Configuration(getId(), visitDetection, visitMerging, validSinceInstant);
    }
}
