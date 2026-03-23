package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.DefaultImportProcessor;
import com.dedicatedcode.reitti.service.ImportStateHolder;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.json.ReaderBasedJsonParser;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.type.TypeFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class GoogleRecordsImporter {
    
    private static final Logger logger = LoggerFactory.getLogger(GoogleRecordsImporter.class);
    
    private final ObjectMapper objectMapper;
    private final ImportStateHolder stateHolder;
    private final DefaultImportProcessor batchProcessor;
    
    public GoogleRecordsImporter(ObjectMapper objectMapper, ImportStateHolder stateHolder, DefaultImportProcessor batchProcessor) {
        this.objectMapper = objectMapper;
        this.stateHolder = stateHolder;
        this.batchProcessor = batchProcessor;
    }
    
    public Map<String, Object> importGoogleRecords(InputStream inputStream, User user) {
        AtomicInteger processedCount = new AtomicInteger(0);
        
        try {
            stateHolder.importStarted();
            logger.info("Importing Google Records file for user {}", user.getUsername());

            JsonParser parser = JsonFactory
                    .builderWithJackson2Defaults()
                    .build()
                    .createParser(ObjectReadContext.empty(), inputStream);

            List<LocationPoint> batch = new ArrayList<>(batchProcessor.getBatchSize());
            boolean foundData = false;
            
            // Look for "locations" array (old Records.json format)
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.PROPERTY_NAME) {
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
                batchProcessor.processBatch(user, batch);
            }
            
            logger.info("Successfully imported and queued {} location points from Google Records for user {}", 
                    processedCount.get(), user.getUsername());
            
            return Map.of(
                    "success", true,
                    "message", "Successfully queued " + processedCount.get() + " location points for processing",
                    "pointsReceived", processedCount.get()
            );
            
        } catch (JacksonException | IOException e) {
            logger.error("Error processing Google Records file", e);
            return Map.of("success", false, "error", "Error processing Google Records file: " + e.getMessage());
        } finally {
            stateHolder.importFinished();
        }
    }
    
    /**
     * Processes the Records.json format with "locations" array
     */
    private int processLocationsArray(JsonParser parser, List<LocationPoint> batch, User user) throws IOException {
        int processedCount = 0;
        
        // Move to the array
        parser.nextToken(); // Should be START_ARRAY
        
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw new IOException("Invalid format: 'locations' is not an array");
        }
        ObjectReader nodeReader = objectMapper.reader()
                .without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

        // Process each location in the array
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() == JsonToken.START_OBJECT) {
                // Parse the location object
                JsonNode locationNode = nodeReader.readTree(parser);
                
                try {
                    LocationPoint point = convertGoogleRecordsLocation(locationNode);
                    if (point != null) {
                        batch.add(point);
                        processedCount++;

                        if (batch.size() >= batchProcessor.getBatchSize()) {
                            batchProcessor.processBatch(user, batch);
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
    private LocationPoint convertGoogleRecordsLocation(JsonNode locationNode) {
        // Check if we have the required fields
        if (!locationNode.has("latitudeE7") ||
                !locationNode.has("longitudeE7") ||
                !locationNode.has("timestamp")) {
            return null;
        }

        LocationPoint point = new LocationPoint();

        // Convert latitudeE7 and longitudeE7 to standard decimal format
        // Google stores these as integers with 7 decimal places of precision
        double latitude = locationNode.get("latitudeE7").asDouble() / 10000000.0;
        double longitude = locationNode.get("longitudeE7").asDouble() / 10000000.0;

        point.setLatitude(latitude);
        point.setLongitude(longitude);
        String timestamp = locationNode.get("timestamp").asString();
        point.setTimestamp(ZonedDateTime.parse(timestamp).toInstant());

        // Set accuracy if available
        if (locationNode.has("accuracy")) {
            point.setAccuracyMeters(locationNode.get("accuracy").asDouble());
        } else {
            point.setAccuracyMeters(10.0);
        }

        return point;
    }
}
