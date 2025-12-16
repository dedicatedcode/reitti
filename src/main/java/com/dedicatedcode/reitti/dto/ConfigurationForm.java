package com.dedicatedcode.reitti.dto;

import com.dedicatedcode.reitti.model.processing.DetectionParameter;
import com.dedicatedcode.reitti.model.processing.RecalculationState;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.*;

public class ConfigurationForm {
    private Long id;
    private String mode = "simple";
    
    // Simple mode
    private Integer sensitivityLevel = 3; // 1-5 scale
    
    // Advanced mode - all Configuration fields
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
    public static ConfigurationForm fromConfiguration(DetectionParameter config, ZoneId zoneId) {
        ConfigurationForm form = new ConfigurationForm();
        form.setId(config.getId());
        
        // Set advanced mode values
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
    
    private static Integer findMatchingSensitivityLevel(DetectionParameter config) {
        // Check each sensitivity level to see if it matches the configuration
        for (int level = 1; level <= 5; level++) {
            DetectionParameter.VisitDetection expectedDetection = mapSensitivityToVisitDetection(level);
            DetectionParameter.VisitMerging expectedMerging = mapSensitivityToVisitMerging(level);
            
            if (configurationMatches(config, expectedDetection, expectedMerging)) {
                return level;
            }
        }
        return null;
    }
    
    private static boolean configurationMatches(DetectionParameter config,
                                                DetectionParameter.VisitDetection expectedDetection,
                                                DetectionParameter.VisitMerging expectedMerging) {
        DetectionParameter.VisitDetection actualDetection = config.getVisitDetection();
        DetectionParameter.VisitMerging actualMerging = config.getVisitMerging();
        
        return actualDetection.getMinimumStayTimeInSeconds() == expectedDetection.getMinimumStayTimeInSeconds() &&
               actualDetection.getMaxMergeTimeBetweenSameStayPoints() == expectedDetection.getMaxMergeTimeBetweenSameStayPoints() &&
               actualMerging.getSearchDurationInHours() == expectedMerging.getSearchDurationInHours() &&
               actualMerging.getMaxMergeTimeBetweenSameVisits() == expectedMerging.getMaxMergeTimeBetweenSameVisits() &&
               actualMerging.getMinDistanceBetweenVisits() == expectedMerging.getMinDistanceBetweenVisits();
    }
    
    private static DetectionParameter.VisitDetection mapSensitivityToVisitDetection(int level) {
        return switch (level) {
            case 1 -> new DetectionParameter.VisitDetection(600, 600);   // Low sensitivity
            case 2 -> new DetectionParameter.VisitDetection(450, 450);
            case 3 -> new DetectionParameter.VisitDetection(300, 300);   // Medium (baseline)
            case 4 -> new DetectionParameter.VisitDetection(225, 225);
            case 5 -> new DetectionParameter.VisitDetection(150, 150);    // High sensitivity
            default -> throw new IllegalArgumentException("Unhandled level [" + level + "] detected!");
        };
    }
    
    private static DetectionParameter.VisitMerging mapSensitivityToVisitMerging(int level) {
        return switch (level) {
            case 1 -> new DetectionParameter.VisitMerging(48, 600, 250);   // Low sensitivity
            case 2 -> new DetectionParameter.VisitMerging(48, 450, 200);
            case 3 -> new DetectionParameter.VisitMerging(48, 300, 150);   // Medium (baseline)
            case 4 -> new DetectionParameter.VisitMerging(48, 225, 100);
            case 5 -> new DetectionParameter.VisitMerging(48, 150, 50);   // High sensitivity
            default -> throw new IllegalArgumentException("Unhandled level [" + level + "] detected!");
        };
    }
    
    // Apply sensitivity level values to advanced mode fields
    public void applySensitivityLevel(Integer level) {
        if (level == null || level < 1 || level > 5) {
            return;
        }
        
        DetectionParameter.VisitDetection visitDetection = mapSensitivityToVisitDetection(level);
        DetectionParameter.VisitMerging visitMerging = mapSensitivityToVisitMerging(level);
        
        this.minimumStayTimeInSeconds = visitDetection.getMinimumStayTimeInSeconds();
        this.maxMergeTimeBetweenSameStayPoints = visitDetection.getMaxMergeTimeBetweenSameStayPoints();
        this.searchDurationInHours = visitMerging.getSearchDurationInHours();
        this.maxMergeTimeBetweenSameVisits = visitMerging.getMaxMergeTimeBetweenSameVisits();
        this.minDistanceBetweenVisits = visitMerging.getMinDistanceBetweenVisits();
        this.sensitivityLevel = level;
    }
    
    // Check if configuration has changed compared to original
    public boolean hasConfigurationChanged(DetectionParameter original) {
        if (original == null) {
            return true; // New configuration
        }
        
        DetectionParameter current = toConfiguration(ZoneId.systemDefault()); // Timezone doesn't matter for comparison
        
        // Compare visit detection parameters
        DetectionParameter.VisitDetection originalDetection = original.getVisitDetection();
        DetectionParameter.VisitDetection currentDetection = current.getVisitDetection();
        
        if (originalDetection.getMinimumStayTimeInSeconds() != currentDetection.getMinimumStayTimeInSeconds() ||
            originalDetection.getMaxMergeTimeBetweenSameStayPoints() != currentDetection.getMaxMergeTimeBetweenSameStayPoints()) {
            return true;
        }
        
        // Compare visit merging parameters
        DetectionParameter.VisitMerging originalMerging = original.getVisitMerging();
        DetectionParameter.VisitMerging currentMerging = current.getVisitMerging();
        
        if (originalMerging.getSearchDurationInHours() != currentMerging.getSearchDurationInHours() ||
            originalMerging.getMaxMergeTimeBetweenSameVisits() != currentMerging.getMaxMergeTimeBetweenSameVisits() ||
            originalMerging.getMinDistanceBetweenVisits() != currentMerging.getMinDistanceBetweenVisits()) {
            return true;
        }
        
        return false;
    }
    
    // Convert to Configuration
    public DetectionParameter toConfiguration(ZoneId timezone) {
        DetectionParameter.VisitDetection visitDetection;
        DetectionParameter.VisitMerging visitMerging;
        
        if ("simple".equals(mode)) {
            // Map sensitivity level to parameters
            visitDetection = mapSensitivityToVisitDetection(sensitivityLevel);
            visitMerging = mapSensitivityToVisitMerging(sensitivityLevel);
        } else {
            // Use advanced mode values
            visitDetection = new DetectionParameter.VisitDetection(
                minimumStayTimeInSeconds,
                maxMergeTimeBetweenSameStayPoints
            );
            visitMerging = new DetectionParameter.VisitMerging(
                searchDurationInHours,
                maxMergeTimeBetweenSameVisits,
                minDistanceBetweenVisits
            );
        }
        
        // Use default location density parameters for now
        DetectionParameter.LocationDensity locationDensity = new DetectionParameter.LocationDensity(50.0, 1440);
        
        Instant validSinceInstant = validSince != null ? ZonedDateTime.of(validSince.atStartOfDay(), timezone).toInstant() : null;
        
        return new DetectionParameter(getId(), visitDetection, visitMerging, locationDensity, validSinceInstant, RecalculationState.NEEDED);
    }
}
