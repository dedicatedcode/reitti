package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.ImportBatchProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public abstract class BaseGoogleTimelineImporter {
    
    private static final Logger logger = LoggerFactory.getLogger(BaseGoogleTimelineImporter.class);

    protected final ObjectMapper objectMapper;
    protected final ImportBatchProcessor batchProcessor;

    public BaseGoogleTimelineImporter(ObjectMapper objectMapper,
                                      ImportBatchProcessor batchProcessor) {
        this.objectMapper = objectMapper;
        this.batchProcessor = batchProcessor;
    }

    protected int handleVisit(User user, ZonedDateTime startTime, ZonedDateTime endTime, LatLng latLng, List<LocationPoint> batch) {
        logger.info("Found visit at [{}] from start [{}] to end [{}].", latLng, startTime, endTime);
        createAndScheduleLocationPoint(latLng, startTime, user, batch);
        createAndScheduleLocationPoint(latLng, endTime, user, batch);
        return 2;
    }

    protected void createAndScheduleLocationPoint(LatLng latLng, ZonedDateTime timestamp, User user, List<LocationPoint> batch) {
        LocationPoint point = new LocationPoint();
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
