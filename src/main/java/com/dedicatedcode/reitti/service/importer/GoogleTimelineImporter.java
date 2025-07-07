package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.service.importer.dto.GoogleTimelineData;
import com.dedicatedcode.reitti.service.importer.dto.SemanticSegment;
import com.dedicatedcode.reitti.service.importer.dto.TimelinePathPoint;
import com.dedicatedcode.reitti.service.importer.dto.Visit;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class GoogleTimelineImporter {
    
    private static final Logger logger = LoggerFactory.getLogger(GoogleTimelineImporter.class);
    private static final Random random = new Random();
    
    private final ObjectMapper objectMapper;
    private final ImportBatchProcessor batchProcessor;
    private final int minStayPointDetectionPoints;
    private final int distanceThresholdMeters;

    public GoogleTimelineImporter(ObjectMapper objectMapper,
                                  ImportBatchProcessor batchProcessor,
                                  @Value("${reitti.staypoint.min-points}") int minStayPointDetectionPoints,
                                  @Value("${reitti.staypoint.distance-threshold-meters}") int distanceThresholdMeters
                                  ) {
        this.objectMapper = objectMapper;
        this.batchProcessor = batchProcessor;
        this.minStayPointDetectionPoints = minStayPointDetectionPoints;
        this.distanceThresholdMeters = distanceThresholdMeters;
    }
    
    public Map<String, Object> importGoogleTimeline(InputStream inputStream, User user) {
        AtomicInteger processedCount = new AtomicInteger(0);
        
        try {
            // Use Jackson's streaming API to process the file
            JsonFactory factory = objectMapper.getFactory();
            JsonParser parser = factory.createParser(inputStream);
            
            List<LocationDataRequest.LocationPoint> batch = new ArrayList<>(batchProcessor.getBatchSize());
            boolean foundData = false;

            GoogleTimelineData timelineData = objectMapper.readValue(parser, GoogleTimelineData.class);
            List<SemanticSegment> semanticSegments = timelineData.getSemanticSegments();
            logger.info("Found {} semantic segments", semanticSegments.size());
            for (SemanticSegment semanticSegment : semanticSegments) {
                if (semanticSegment.getVisit() != null) {
                    Visit visit = semanticSegment.getVisit();
                    logger.info("Found visit at [{}] from start [{}] to end [{}]. Will insert at least [{}] synthetic geo locations.", visit.getTopCandidate().getPlaceLocation().getLatLng(), semanticSegment.getStartTime(), semanticSegment.getEndTime(), minStayPointDetectionPoints);

                    Optional<LatLng> latLng = parseLatLng(visit.getTopCandidate().getPlaceLocation().getLatLng());
                    if (latLng.isPresent()) {
                        createAndScheduleLocationPoint(latLng.get(), semanticSegment.getStartTime(), user, batch);
                        processedCount.incrementAndGet();
                        ZonedDateTime startTime = ZonedDateTime.parse(semanticSegment.getStartTime());
                        ZonedDateTime endTime = ZonedDateTime.parse(semanticSegment.getEndTime());
                        long durationBetween = Duration.between(startTime.toInstant(), endTime.toInstant()).toSeconds();
                        long increment = Math.max(10, durationBetween / (minStayPointDetectionPoints * 10L));
                        ZonedDateTime currentTime = startTime.plusSeconds(increment);
                        while (currentTime.isBefore(endTime)) {
                            // Move randomly around the visit location within the distance threshold
                            LatLng randomizedLocation = addRandomOffset(latLng.get(), distanceThresholdMeters / 3);
                            createAndScheduleLocationPoint(randomizedLocation, currentTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), user, batch);
                            processedCount.incrementAndGet();
                            currentTime = currentTime.plusSeconds(increment);
                        }
                        createAndScheduleLocationPoint(latLng.get(), semanticSegment.getEndTime(), user, batch);
                        processedCount.incrementAndGet();

                    }
                }

                if (semanticSegment.getTimelinePath() != null) {
                    List<TimelinePathPoint> timelinePath = semanticSegment.getTimelinePath();
                    logger.info("Found timeline path from start [{}] to end [{}]. Will insert [{}] synthetic geo locations based on timeline path.", semanticSegment.getStartTime(), semanticSegment.getEndTime(), timelinePath.size());
                    for (TimelinePathPoint timelinePathPoint : timelinePath) {
                        parseLatLng(timelinePathPoint.getPoint()).ifPresent(location -> {
                            createAndScheduleLocationPoint(location, timelinePathPoint.getTime(), user, batch);
                            processedCount.incrementAndGet();
                        });
                    }
                }
            }

            // Process any remaining locations
            if (!batch.isEmpty()) {
                batchProcessor.sendToQueue(user, batch);
            }
            
            logger.info("Successfully imported and queued {} location points from Google Timeline for user {}", 
                    processedCount.get(), user.getUsername());
            
            return Map.of(
                    "success", true,
                    "message", "Successfully queued " + processedCount.get() + " location points for processing",
                    "pointsReceived", processedCount.get()
            );
            
        } catch (IOException e) {
            logger.error("Error processing Google Timeline file", e);
            return Map.of("success", false, "error", "Error processing Google Timeline file: " + e.getMessage());
        }
    }


    void createAndScheduleLocationPoint(LatLng latLng, String timestamp, User user, List<LocationDataRequest.LocationPoint> batch) {
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

    private Optional<LatLng> parseLatLng(String input) {
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
    private LatLng addRandomOffset(LatLng original, int maxDistanceMeters) {
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

    private record LatLng(double latitude, double longitude) {}

    /**
     * Parses a LatLng string in format "53.8633043°, 10.7011529°" or "geo:55.605843,13.007508" to extract latitude and longitude
     */
    private String[] parseLatLngString(String latLngStr) {
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
