package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public abstract class BaseGoogleTimelineImporter {
    
    private static final Logger logger = LoggerFactory.getLogger(BaseGoogleTimelineImporter.class);

    protected final ObjectMapper objectMapper;
    protected final ImportBatchProcessor batchProcessor;
    protected final int minStayPointDetectionPoints;
    protected final int distanceThresholdMeters;
    protected final int mergeThresholdSeconds;

    public BaseGoogleTimelineImporter(ObjectMapper objectMapper,
                                      ImportBatchProcessor batchProcessor,
                                      @Value("${reitti.staypoint.min-points}") int minStayPointDetectionPoints,
                                      @Value("${reitti.staypoint.distance-threshold-meters}") int distanceThresholdMeters,
                                      @Value("${reitti.visit.merge-threshold-seconds}") int mergeThresholdSeconds) {
        this.objectMapper = objectMapper;
        this.batchProcessor = batchProcessor;
        this.minStayPointDetectionPoints = minStayPointDetectionPoints;
        this.distanceThresholdMeters = distanceThresholdMeters;
        this.mergeThresholdSeconds = mergeThresholdSeconds;
    }

    protected int handleVisit(User user, ZonedDateTime startTime, ZonedDateTime endTime, LatLng latLng, List<LocationDataRequest.LocationPoint> batch) {
        logger.info("Found visit at [{}] from start [{}] to end [{}]. Will insert at least [{}] synthetic geo locations.", latLng, startTime, endTime, minStayPointDetectionPoints);
        createAndScheduleLocationPoint(latLng, startTime, user, batch);
        int count = 1;
        long durationBetween = Duration.between(startTime.toInstant(), endTime.toInstant()).toSeconds();
        if (durationBetween > mergeThresholdSeconds) {
            long increment = 60;
            ZonedDateTime currentTime = startTime.plusSeconds(increment);
            while (currentTime.isBefore(endTime)) {
                createAndScheduleLocationPoint(latLng, currentTime, user, batch);
                count+=1;
                currentTime = currentTime.plusSeconds(increment);
            }
            logger.debug("Inserting synthetic points into import to simulate stays at [{}] from [{}] till [{}]", latLng, startTime, endTime);
        } else {
            logger.info("Skipping creating synthetic points at [{}] since duration was less then [{}] seconds ", latLng, mergeThresholdSeconds);
        }
        createAndScheduleLocationPoint(latLng, endTime, user, batch);
        return count + 1;
    }

    protected void createAndScheduleLocationPoint(LatLng latLng, ZonedDateTime timestamp, User user, List<LocationDataRequest.LocationPoint> batch) {
        LocationDataRequest.LocationPoint point = new LocationDataRequest.LocationPoint();
        point.setLatitude(latLng.latitude);
        point.setLongitude(latLng.longitude);
        point.setTimestamp(timestamp.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        point.setAccuracyMeters(10.0);
        batch.add(point);
        logger.trace("Created location point at [{}]", point);
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
