package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Optional;
import java.util.Random;

public abstract class BaseGoogleTimelineImporter {
    
    protected static final Logger logger = LoggerFactory.getLogger(BaseGoogleTimelineImporter.class);
    protected static final Random random = new Random();
    
    protected final ObjectMapper objectMapper;
    protected final ImportBatchProcessor batchProcessor;
    protected final int minStayPointDetectionPoints;
    protected final int distanceThresholdMeters;
    protected final int mergeThresholdSeconds;

    public BaseGoogleTimelineImporter(ObjectMapper objectMapper,
                                      ImportBatchProcessor batchProcessor,
                                      @Value("${reitti.staypoint.min-points}") int minStayPointDetectionPoints,
                                      @Value("${reitti.staypoint.distance-threshold-meters}") int distanceThresholdMeters,
                                      @Value("${reitti.staypoint.merge-threshold-seconds}") int mergeThresholdSeconds) {
        this.objectMapper = objectMapper;
        this.batchProcessor = batchProcessor;
        this.minStayPointDetectionPoints = minStayPointDetectionPoints;
        this.distanceThresholdMeters = distanceThresholdMeters;
        this.mergeThresholdSeconds = mergeThresholdSeconds;
    }

    protected void createAndScheduleLocationPoint(LatLng latLng, String timestamp, User user, List<LocationDataRequest.LocationPoint> batch) {
        LocationDataRequest.LocationPoint point = new LocationDataRequest.LocationPoint();
        point.setLatitude(latLng.latitude);
        point.setLongitude(latLng.longitude);
        point.setTimestamp(timestamp);
        point.setAccuracyMeters(10.0);
        batch.add(point);
        if (batch.size() >= batchProcessor.getBatchSize()) {
            batchProcessor.sendToQueue(user, batch);
            batch.clear();
        }
    }

    protected Optional<LatLng> parseLatLng(String input) {
        try {
            String[] coords = parseLatLngString(input);
            if (coords == null) {
                return Optional.empty();
            }
            return Optional.of(new LatLng(Double.parseDouble(coords[0]), Double.parseDouble(coords[1])));
        } catch (NumberFormatException e) {
            logger.warn("Error parsing LatLng string: {}", input);
            return Optional.empty();
        }
    }

    /**
     * Adds a random offset to a location within the specified distance threshold
     */
    protected LatLng addRandomOffset(LatLng original, int maxDistanceMeters) {
        // Convert distance to approximate degrees (rough approximation)
        // 1 degree latitude ≈ 111,000 meters
        // 1 degree longitude ≈ 111,000 * cos(latitude) meters
        double latOffsetDegrees = (maxDistanceMeters / 111000.0) * (random.nextDouble() * 2 - 1);
        double lonOffsetDegrees = (maxDistanceMeters / (111000.0 * Math.cos(Math.toRadians(original.latitude)))) * (random.nextDouble() * 2 - 1);
        
        // Ensure we don't exceed the maximum distance by scaling if necessary
        double actualDistance = Math.sqrt(latOffsetDegrees * latOffsetDegrees + lonOffsetDegrees * lonOffsetDegrees) * 111000.0;
        if (actualDistance > maxDistanceMeters) {
            double scale = maxDistanceMeters / actualDistance;
            latOffsetDegrees *= scale;
            lonOffsetDegrees *= scale;
        }
        
        return new LatLng(
            original.latitude + latOffsetDegrees,
            original.longitude + lonOffsetDegrees
        );
    }

    protected record LatLng(double latitude, double longitude) {}

    /**
     * Parses a LatLng string in format "53.8633043°, 10.7011529°" or "geo:55.605843,13.007508" to extract latitude and longitude
     */
    protected String[] parseLatLngString(String latLngStr) {
        if (latLngStr == null || latLngStr.trim().isEmpty()) {
            return null;
        }
        
        try {
            String cleaned = latLngStr.trim();
            
            // Handle geo: format
            if (cleaned.startsWith("geo:")) {
                cleaned = cleaned.substring(4); // Remove "geo:" prefix
            } else {
                // Handle degree format - remove degree symbols
                cleaned = cleaned.replace("°", "");
            }
            
            String[] parts = cleaned.split(",");
            
            if (parts.length != 2) {
                return null;
            }
            
            String latStr = parts[0].trim();
            String lngStr = parts[1].trim();
            
            // Validate that they are valid numbers
            Double.parseDouble(latStr);
            Double.parseDouble(lngStr);
            
            return new String[]{latStr, lngStr};
        } catch (Exception e) {
            logger.warn("Failed to parse LatLng string: {}", latLngStr);
            return null;
        }
    }
}
