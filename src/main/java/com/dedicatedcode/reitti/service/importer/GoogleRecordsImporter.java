package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.model.User;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class GoogleRecordsImporter {
    
    private static final Logger logger = LoggerFactory.getLogger(GoogleRecordsImporter.class);
    
    private final ObjectMapper objectMapper;
    private final ImportBatchProcessor batchProcessor;
    
    public GoogleRecordsImporter(ObjectMapper objectMapper, ImportBatchProcessor batchProcessor) {
        this.objectMapper = objectMapper;
        this.batchProcessor = batchProcessor;
    }
    
    public Map<String, Object> importGoogleRecords(InputStream inputStream, User user) {
        AtomicInteger processedCount = new AtomicInteger(0);
        
        try {
            // Use Jackson's streaming API to process the file
            JsonFactory factory = objectMapper.getFactory();
            JsonParser parser = factory.createParser(inputStream);
            
            List<LocationDataRequest.LocationPoint> batch = new ArrayList<>(batchProcessor.getBatchSize());
            boolean foundData = false;
            
            // Look for "locations" array (old Records.json format)
            while (parser.nextToken() != null) {
                if (parser.getCurrentToken() == JsonToken.FIELD_NAME) {
                    String fieldName = parser.currentName();
                    
                    if ("locations".equals(fieldName)) {
                        foundData = true;
                        processedCount.addAndGet(processLocationsArray(parser, batch, user));
                        break;
                    }
                }
            }
            
            if (!foundData) {
                return Map.of("success", false, "error", "Invalid format: 'locations' array not found in Records.json");
            }
            
            // Process any remaining locations
            if (!batch.isEmpty()) {
                batchProcessor.sendToQueue(user, batch);
            }
            
            logger.info("Successfully imported and queued {} location points from Google Records for user {}", 
                    processedCount.get(), user.getUsername());
            
            return Map.of(
                    "success", true,
                    "message", "Successfully queued " + processedCount.get() + " location points for processing",
                    "pointsReceived", processedCount.get()
            );
            
        } catch (IOException e) {
            logger.error("Error processing Google Records file", e);
            return Map.of("success", false, "error", "Error processing Google Records file: " + e.getMessage());
        }
    }
    
    /**
     * Processes the Records.json format with "locations" array
     */
    private int processLocationsArray(JsonParser parser, List<LocationDataRequest.LocationPoint> batch, User user) throws IOException {
        int processedCount = 0;
        
        // Move to the array
        parser.nextToken(); // Should be START_ARRAY
        
        if (parser.getCurrentToken() != JsonToken.START_ARRAY) {
            throw new IOException("Invalid format: 'locations' is not an array");
        }
        
        // Process each location in the array
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.getCurrentToken() == JsonToken.START_OBJECT) {
                // Parse the location object
                JsonNode locationNode = objectMapper.readTree(parser);
                
                try {
                    LocationDataRequest.LocationPoint point = convertGoogleRecordsLocation(locationNode);
                    if (point != null) {
                        batch.add(point);
                        processedCount++;

                        if (batch.size() >= batchProcessor.getBatchSize()) {
                            batchProcessor.sendToQueue(user, batch);
                            batch.clear();
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Error processing location entry: {}", e.getMessage());
                }
            }
        }
        
        return processedCount;
    }
    
    /**
     * Converts a Google Records location entry to our LocationPoint format
     */
    private LocationDataRequest.LocationPoint convertGoogleRecordsLocation(JsonNode locationNode) {
        // Check if we have the required fields
        if (!locationNode.has("latitudeE7") ||
                !locationNode.has("longitudeE7") ||
                !locationNode.has("timestamp")) {
            return null;
        }

        LocationDataRequest.LocationPoint point = new LocationDataRequest.LocationPoint();

        // Convert latitudeE7 and longitudeE7 to standard decimal format
        // Google stores these as integers with 7 decimal places of precision
        double latitude = locationNode.get("latitudeE7").asDouble() / 10000000.0;
        double longitude = locationNode.get("longitudeE7").asDouble() / 10000000.0;

        point.setLatitude(latitude);
        point.setLongitude(longitude);
        point.setTimestamp(locationNode.get("timestamp").asText());

        // Set accuracy if available
        if (locationNode.has("accuracy")) {
            point.setAccuracyMeters(locationNode.get("accuracy").asDouble());
        } else {
            point.setAccuracyMeters(100.0);
        }

        return point;
    }
}
