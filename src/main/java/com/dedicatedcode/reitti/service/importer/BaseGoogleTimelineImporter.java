package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.model.processing.DetectionParameter;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.ImportBatchProcessor;
import com.dedicatedcode.reitti.service.VisitDetectionParametersService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public abstract class BaseGoogleTimelineImporter {
    
    private static final Logger logger = LoggerFactory.getLogger(BaseGoogleTimelineImporter.class);

    protected final ObjectMapper objectMapper;
    protected final ImportBatchProcessor batchProcessor;
    private final VisitDetectionParametersService parametersService;

    public BaseGoogleTimelineImporter(ObjectMapper objectMapper,
                                      ImportBatchProcessor batchProcessor,
                                      VisitDetectionParametersService parametersService) {
        this.objectMapper = objectMapper;
        this.batchProcessor = batchProcessor;
        this.parametersService = parametersService;
    }

    protected int handleVisit(User user, ZonedDateTime startTime, ZonedDateTime endTime, LatLng latLng, List<LocationDataRequest.LocationPoint> batch) {
        DetectionParameter detectionParameter = parametersService.getCurrentConfiguration(user, startTime.toInstant());

        logger.info("Found visit at [{}] from start [{}] to end [{}]. Will insert at least [{}] synthetic geo locations.", latLng, startTime, endTime, detectionParameter.getVisitDetection().getMinimumAdjacentPoints());
        createAndScheduleLocationPoint(latLng, startTime, user, batch);
        int count = 1;

        long durationBetween = Duration.between(startTime.toInstant(), endTime.toInstant()).toSeconds();
        if (durationBetween > detectionParameter.getVisitDetection().getMinimumStayTimeInSeconds()) {
            long increment = 60;
            ZonedDateTime currentTime = startTime.plusSeconds(increment);
            while (currentTime.isBefore(endTime)) {
                createAndScheduleLocationPoint(latLng, currentTime, user, batch);
                count+=1;
                currentTime = currentTime.plusSeconds(increment);
            }
            logger.debug("Inserting synthetic points into import to simulate stays at [{}] from [{}] till [{}]", latLng, startTime, endTime);
        } else {
            logger.info("Skipping creating synthetic points at [{}] since duration was less then [{}] seconds ", latLng, detectionParameter.getVisitDetection().getMinimumStayTimeInSeconds());
        }
        createAndScheduleLocationPoint(latLng, endTime, user, batch);
        return count + 1;
    }

    protected void createAndScheduleLocationPoint(LatLng latLng, ZonedDateTime timestamp, User user, List<LocationDataRequest.LocationPoint> batch) {
        LocationDataRequest.LocationPoint point = new LocationDataRequest.LocationPoint();
        point.setLatitude(latLng.latitude);
        point.setLongitude(latLng.longitude);
        point.setTimestamp(timestamp.withNano(0).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
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
