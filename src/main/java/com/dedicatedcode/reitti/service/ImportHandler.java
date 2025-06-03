package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.model.User;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ImportHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(ImportHandler.class);
    
    private final ObjectMapper objectMapper;
    private final ImportListener importListener;
    private final int batchSize;

    @Autowired
    public ImportHandler(
            ObjectMapper objectMapper,
            ImportListener importListener,
            @Value("${reitti.import.batch-size:100}") int batchSize) {
        this.objectMapper = objectMapper;
        this.importListener = importListener;
        this.batchSize = batchSize;
    }
    
    public Map<String, Object> importGoogleTakeout(InputStream inputStream, User user) {
        AtomicInteger processedCount = new AtomicInteger(0);
        
        try {
            // Use Jackson's streaming API to process the file
            JsonFactory factory = objectMapper.getFactory();
            JsonParser parser = factory.createParser(inputStream);
            
            // Find the "locations" array
            while (parser.nextToken() != null) {
                if (parser.getCurrentToken() == JsonToken.FIELD_NAME && 
                    "locations".equals(parser.getCurrentName())) {
                    
                    // Move to the array
                    parser.nextToken(); // Should be START_ARRAY
                    
                    if (parser.getCurrentToken() != JsonToken.START_ARRAY) {
                        return Map.of("success", false, "error", "Invalid format: 'locations' is not an array");
                    }
                    
                    List<LocationDataRequest.LocationPoint> batch = new ArrayList<>(batchSize);
                    
                    // Process each location in the array
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        if (parser.getCurrentToken() == JsonToken.START_OBJECT) {
                            // Parse the location object
                            JsonNode locationNode = objectMapper.readTree(parser);
                            
                            try {
                                LocationDataRequest.LocationPoint point = convertGoogleTakeoutLocation(locationNode);
                                if (point != null) {
                                    batch.add(point);
                                    processedCount.incrementAndGet();
                                    
                                    // Process in batches to avoid memory issues
                                    if (batch.size() >= batchSize) {
                                        this.importListener.handleImport(user, new ArrayList<>(batch));
                                        logger.info("Queued batch of {} locations for processing", batch.size());
                                        batch.clear();
                                    }
                                }
                            } catch (Exception e) {
                                logger.warn("Error processing location entry: {}", e.getMessage());
                                // Continue with next location
                            }
                        }
                    }
                    
                    // Process any remaining locations
                    if (!batch.isEmpty()) {
                        this.importListener.handleImport(user, new ArrayList<>(batch));
                        logger.info("Queued final batch of {} locations for processing", batch.size());
                    }
                    
                    break; // We've processed the locations array, no need to continue
                }
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
     * Converts a Google Takeout location entry to our LocationPoint format
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
    
    public Map<String, Object> importGpx(InputStream inputStream, User user) {
        AtomicInteger processedCount = new AtomicInteger(0);
        
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(inputStream);
            
            // Normalize the XML structure
            document.getDocumentElement().normalize();
            
            // Get all track points (trkpt) from the GPX file
            NodeList trackPoints = document.getElementsByTagName("trkpt");
            
            List<LocationDataRequest.LocationPoint> batch = new ArrayList<>(batchSize);
            
            // Process each track point
            for (int i = 0; i < trackPoints.getLength(); i++) {
                Element trackPoint = (Element) trackPoints.item(i);
                
                try {
                    LocationDataRequest.LocationPoint point = convertGpxTrackPoint(trackPoint);
                    if (point != null) {
                        batch.add(point);
                        processedCount.incrementAndGet();
                        
                        // Process in batches to avoid memory issues
                        if (batch.size() >= batchSize) {
                            this.importListener.handleImport(user, new ArrayList<>(batch));
                            logger.info("Queued batch of {} locations for processing", batch.size());
                            batch.clear();
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Error processing GPX track point: {}", e.getMessage());
                    // Continue with next point
                }
            }
            
            // Process any remaining locations
            if (!batch.isEmpty()) {
                // Create and publish event to RabbitMQ
                this.importListener.handleImport(user, new ArrayList<>(batch));
                logger.info("Queued final batch of {} locations for processing", batch.size());
            }
            
            logger.info("Successfully imported and queued {} location points from GPX file for user {}", 
                    processedCount.get(), user.getUsername());
            
            return Map.of(
                    "success", true,
                    "message", "Successfully queued " + processedCount.get() + " location points for processing",
                    "pointsReceived", processedCount.get()
            );
            
        } catch (Exception e) {
            logger.error("Error processing GPX file", e);
            return Map.of("success", false, "error", "Error processing GPX file: " + e.getMessage());
        }
    }
    
    /**
     * Converts a GPX track point to our LocationPoint format
     */
    private LocationDataRequest.LocationPoint convertGpxTrackPoint(Element trackPoint) {
        // Check if we have the required attributes
        if (!trackPoint.hasAttribute("lat") || !trackPoint.hasAttribute("lon")) {
            return null;
        }
        
        LocationDataRequest.LocationPoint point = new LocationDataRequest.LocationPoint();
        
        // Get latitude and longitude
        double latitude = Double.parseDouble(trackPoint.getAttribute("lat"));
        double longitude = Double.parseDouble(trackPoint.getAttribute("lon"));
        
        point.setLatitude(latitude);
        point.setLongitude(longitude);
        
        // Get timestamp from the time element
        NodeList timeElements = trackPoint.getElementsByTagName("time");
        if (timeElements.getLength() > 0) {
            String timeStr = timeElements.item(0).getTextContent();
            point.setTimestamp(timeStr);
        } else {
            return null;
        }
        
        // Set accuracy - GPX doesn't typically include accuracy, so use a default
        point.setAccuracyMeters(10.0); // Default accuracy of 10 meters
        
        return point;
    }
}
