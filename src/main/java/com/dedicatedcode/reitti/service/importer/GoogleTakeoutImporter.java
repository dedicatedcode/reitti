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
public class GoogleTakeoutImporter {
    
    private static final Logger logger = LoggerFactory.getLogger(GoogleTakeoutImporter.class);
    
    private final ObjectMapper objectMapper;
    private final ImportBatchProcessor batchProcessor;
    
    public GoogleTakeoutImporter(ObjectMapper objectMapper, ImportBatchProcessor batchProcessor) {
        this.objectMapper = objectMapper;
        this.batchProcessor = batchProcessor;
    }
    
    public Map<String, Object> importGoogleTakeout(InputStream inputStream, User user) {
        AtomicInteger processedCount = new AtomicInteger(0);
        
        try {
            // Use Jackson's streaming API to process the file
            JsonFactory factory = objectMapper.getFactory();
            JsonParser parser = factory.createParser(inputStream);
            
            List<LocationDataRequest.LocationPoint> batch = new ArrayList<>(batchProcessor.getBatchSize());
            boolean foundData = false;
            
            // Look for either "locations" array (old format) or "rawSignals" array (new format)
            while (parser.nextToken() != null) {
                if (parser.getCurrentToken() == JsonToken.FIELD_NAME) {
                    String fieldName = parser.currentName();
                    
                    if ("locations".equals(fieldName)) {
                        // Old Records.json format
                        foundData = true;
                        processedCount.addAndGet(processLocationsArray(parser, batch, user));
                        break;
                    } else if ("rawSignals".equals(fieldName)) {
                        // New format with rawSignals array
                        foundData = true;
                        processedCount.addAndGet(processRawSignalsArray(parser, batch, user));
                        break;
                    }
                }
            }
            
            if (!foundData) {
                return Map.of("success", false, "error", "Invalid format: neither 'locations' nor 'rawSignals' array found");
            }
            
            // Process any remaining locations
            if (!batch.isEmpty()) {
                batchProcessor.sendToQueue(user, batch);
            }
            
            logger.info("Successfully imported and queued {} location points from Google Takeout for user {}", 
                    processedCount.get(), user.getUsername());
            
            return Map.of(
                    "success", true,
                    "message", "Successfully queued " + processedCount.get() + " location points for processing",
                    "pointsReceived", processedCount.get()
            );
            
        } catch (IOException e) {
            logger.error("Error processing Google Takeout file", e);
            return Map.of("success", false, "error", "Error processing Google Takeout file: " + e.getMessage());
        }
    }
    
    /**
     * Processes the old Records.json format with "locations" array
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
                    LocationDataRequest.LocationPoint point = convertGoogleTakeoutLocation(locationNode);
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
     * Processes the new format with "rawSignals" array containing position elements
     */
    private int processRawSignalsArray(JsonParser parser, List<LocationDataRequest.LocationPoint> batch, User user) throws IOException {
        int processedCount = 0;
        
        // Move to the array
        parser.nextToken(); // Should be START_ARRAY
        
        if (parser.getCurrentToken() != JsonToken.START_ARRAY) {
            throw new IOException("Invalid format: 'rawSignals' is not an array");
        }
        
        // Process each signal in the array
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.getCurrentToken() == JsonToken.START_OBJECT) {
                // Parse the signal object
                JsonNode signalNode = objectMapper.readTree(parser);
                
                try {
                    // Check if this signal contains position data
                    if (signalNode.has("position")) {
                        LocationDataRequest.LocationPoint point = convertRawSignalPosition(signalNode);
                        if (point != null) {
                            batch.add(point);
                            processedCount++;

                            if (batch.size() >= batchProcessor.getBatchSize()) {
                                batchProcessor.sendToQueue(user, batch);
                                batch.clear();
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Error processing raw signal entry: {}", e.getMessage());
                }
            }
        }
        
        return processedCount;
    }
    
    /**
     * Converts a Google Takeout location entry to our LocationPoint format (old Records.json format)
     */
    private LocationDataRequest.LocationPoint convertGoogleTakeoutLocation(JsonNode locationNode) {
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
    
    /**
     * Converts a raw signal with position data to our LocationPoint format (new format)
     */
    private LocationDataRequest.LocationPoint convertRawSignalPosition(JsonNode signalNode) {
        JsonNode positionNode = signalNode.get("position");
        if (positionNode == null) {
            return null;
        }
        
        double latitude, longitude;
        
        if (positionNode.has("LatLng")) {
            // New format: "LatLng": "53.8633043°, 10.7011529°"
            String latLngStr = positionNode.get("LatLng").asText();
            try {
                String[] coords = parseLatLngString(latLngStr);
                if (coords == null) {
                    return null;
                }
                latitude = Double.parseDouble(coords[0]);
                longitude = Double.parseDouble(coords[1]);
            } catch (NumberFormatException e) {
                logger.warn("Error parsing LatLng string: {}", latLngStr);
                return null;
            }
        } else {
            return null;
        }
        
        // Check for timestamp - it might be in the signal node or position node
        String timestamp = null;
        if (signalNode.has("timestamp")) {
            timestamp = signalNode.get("timestamp").asText();
        } else if (positionNode.has("timestamp")) {
            timestamp = positionNode.get("timestamp").asText();
        }
        
        if (timestamp == null || timestamp.isEmpty()) {
            return null;
        }

        LocationDataRequest.LocationPoint point = new LocationDataRequest.LocationPoint();

        point.setLatitude(latitude);
        point.setLongitude(longitude);
        point.setTimestamp(timestamp);

        // Set accuracy if available (check both signal and position nodes)
        Double accuracy = null;
        if (positionNode.has("accuracyMeters")) {
            accuracy = positionNode.get("accuracyMeters").asDouble();
        } else if (positionNode.has("accuracy")) {
            accuracy = positionNode.get("accuracy").asDouble();
        } else if (signalNode.has("accuracy")) {
            accuracy = signalNode.get("accuracy").asDouble();
        }
        
        point.setAccuracyMeters(accuracy != null ? accuracy : 100.0);

        return point;
    }
    
    /**
     * Parses a LatLng string in format "53.8633043°, 10.7011529°" to extract latitude and longitude
     */
    private String[] parseLatLngString(String latLngStr) {
        if (latLngStr == null || latLngStr.trim().isEmpty()) {
            return null;
        }
        
        try {
            // Remove degree symbols and split by comma
            String cleaned = latLngStr.replace("°", "").trim();
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
