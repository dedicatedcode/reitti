package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.config.LocationDensityConfig;
import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.geo.GeoUtils;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.processing.DetectionParameter;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.service.VisitDetectionParametersService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LocationDataDensityNormalizer {
    
    private static final Logger logger = LoggerFactory.getLogger(LocationDataDensityNormalizer.class);
    
    private final LocationDensityConfig config;
    private final RawLocationPointJdbcService rawLocationPointService;
    private final SyntheticLocationPointGenerator syntheticGenerator;
    private final VisitDetectionParametersService visitDetectionParametersService;
    
    @Autowired
    public LocationDataDensityNormalizer(
            LocationDensityConfig config,
            RawLocationPointJdbcService rawLocationPointService,
            SyntheticLocationPointGenerator syntheticGenerator,
            VisitDetectionParametersService visitDetectionParametersService) {
        this.config = config;
        this.rawLocationPointService = rawLocationPointService;
        this.syntheticGenerator = syntheticGenerator;
        this.visitDetectionParametersService = visitDetectionParametersService;
    }
    
    public void normalizeAroundPoint(User user, LocationPoint newPoint) {
        try {
            logger.trace("Starting density normalization around point at {} for user {}",
                newPoint.getTimestamp(), user.getUsername());
            
            // Get user's detection parameters for interpolation limits
            Instant pointTime = Instant.parse(newPoint.getTimestamp());
            DetectionParameter detectionParams = visitDetectionParametersService
                .getCurrentConfiguration(user, pointTime);
            DetectionParameter.LocationDensity densityConfig = detectionParams.getLocationDensity();
            
            // Create time window around the new point
            Duration window = Duration.ofMinutes(densityConfig.getMaxInterpolationGapMinutes());
            List<RawLocationPoint> surroundingPoints = rawLocationPointService
                .findSurroundingPoints(user, pointTime, window);
            
            // Sort points by timestamp
            surroundingPoints.sort(Comparator.comparing(RawLocationPoint::getTimestamp));
            
            logger.trace("Found {} surrounding points in window of {} minutes",
                surroundingPoints.size(), densityConfig.getMaxInterpolationGapMinutes());
            
            // Process gaps and excess density
            processGaps(user, surroundingPoints, densityConfig);
            handleExcessDensity(user, surroundingPoints);
            
        } catch (Exception e) {
            logger.error("Error during density normalization for user {} around point {}: {}", 
                user.getUsername(), newPoint.getTimestamp(), e.getMessage(), e);
        }
    }
    
    private void processGaps(User user, List<RawLocationPoint> points, DetectionParameter.LocationDensity densityConfig) {
        if (points.size() < 2) {
            return;
        }
        
        int gapThresholdSeconds = config.getGapThresholdSeconds();
        List<LocationPoint> allSyntheticPoints = new ArrayList<>();
        
        for (int i = 0; i < points.size() - 1; i++) {
            RawLocationPoint current = points.get(i);
            RawLocationPoint next = points.get(i + 1);
            
            // Calculate gap duration
            long gapSeconds = Duration.between(current.getTimestamp(), next.getTimestamp()).getSeconds();
            
            if (gapSeconds > gapThresholdSeconds) {
                logger.trace("Found gap of {} seconds between {} and {}",
                    gapSeconds, current.getTimestamp(), next.getTimestamp());
                
                // Check if gap is within interpolation limits
                if (gapSeconds <= densityConfig.getMaxInterpolationGapMinutes() * 60) {
                    // Generate synthetic points for this gap
                    List<LocationPoint> syntheticPoints = syntheticGenerator.generateSyntheticPoints(
                        current, 
                        next, 
                        config.getTargetPointsPerMinute(),
                        densityConfig.getMaxInterpolationDistanceMeters()
                    );
                    
                    allSyntheticPoints.addAll(syntheticPoints);
                }
            }
        }
        
        // Insert all synthetic points
        if (!allSyntheticPoints.isEmpty()) {
            int inserted = rawLocationPointService.bulkInsertSynthetic(user, allSyntheticPoints);
            logger.debug("Inserted {} synthetic points for user {}", inserted, user.getUsername());
        }
    }
    
    private void handleExcessDensity(User user, List<RawLocationPoint> points) {
        if (points.size() < 2) {
            return;
        }
        
        int toleranceSeconds = config.getToleranceSeconds();
        List<Long> pointsToIgnore = new ArrayList<>();
        
        for (int i = 0; i < points.size() - 1; i++) {
            RawLocationPoint current = points.get(i);
            RawLocationPoint next = points.get(i + 1);
            
            // Calculate time difference
            long timeDiff = Duration.between(current.getTimestamp(), next.getTimestamp()).getSeconds();
            
            if (timeDiff < toleranceSeconds) {
                // Points are too close together - decide which one to ignore
                RawLocationPoint toIgnore = selectPointToIgnore(current, next);
                
                if (toIgnore != null && !toIgnore.isIgnored()) {
                    pointsToIgnore.add(toIgnore.getId());
                    logger.trace("Marking point {} as ignored due to excess density", toIgnore.getId());
                }
            }
        }
        
        // Update ignored status
        if (!pointsToIgnore.isEmpty()) {
            rawLocationPointService.bulkUpdateIgnoredStatus(pointsToIgnore, true);
            logger.debug("Marked {} points as ignored for user {}", pointsToIgnore.size(), user.getUsername());
        }
    }
    
    private RawLocationPoint selectPointToIgnore(RawLocationPoint point1, RawLocationPoint point2) {
        // Priority rules: Real points > Recent synthetic > Old synthetic
        
        // Never ignore real points if the other is synthetic
        if (!point1.isSynthetic() && point2.isSynthetic()) {
            return point2;
        }
        if (point1.isSynthetic() && !point2.isSynthetic()) {
            return point1;
        }
        
        // If both are real or both are synthetic, prefer the one with better accuracy
        Double acc1 = point1.getAccuracyMeters();
        Double acc2 = point2.getAccuracyMeters();
        
        if (acc1 != null && acc2 != null) {
            // Lower accuracy value is better
            return acc1 < acc2 ? point2 : point1;
        }
        
        if (acc1 != null) {
            return point2; // point1 has accuracy info, prefer it
        }
        
        if (acc2 != null) {
            return point1; // point2 has accuracy info, prefer it
        }
        
        // If no accuracy info, prefer the more recent point
        return point1.getTimestamp().isBefore(point2.getTimestamp()) ? point1 : point2;
    }
}
