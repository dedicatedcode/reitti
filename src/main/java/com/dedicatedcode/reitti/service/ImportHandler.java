package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.model.User;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ImportHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(ImportHandler.class);
    
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final int batchSize;

    @Autowired
    public ImportHandler(
            ObjectMapper objectMapper,
            RabbitTemplate rabbitTemplate,
            @Value("${reitti.import.batch-size:100}") int batchSize) {
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
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
                    "locations".equals(parser.currentName())) {
                    
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
                                        sendToQueue(user, batch);
                                        batch.clear();
                                    }
                                }
                            } catch (Exception e) {
                                logger.warn("Error processing location entry: {}", e.getMessage());
                            }
                        }
                    }
                    
                    // Process any remaining locations
                    if (!batch.isEmpty()) {
                        sendToQueue(user, batch);
                    }
                    
                    break;
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
                            sendToQueue(user, batch);
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
                sendToQueue(user, batch);
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

    public Map<String, Object> importGeoJson(InputStream inputStream, User user) {
        AtomicInteger processedCount = new AtomicInteger(0);

        try {
            JsonNode rootNode = objectMapper.readTree(inputStream);

            // Check if it's a valid GeoJSON
            if (!rootNode.has("type")) {
                return Map.of("success", false, "error", "Invalid GeoJSON: missing 'type' field");
            }

            String type = rootNode.get("type").asText();
            List<LocationDataRequest.LocationPoint> batch = new ArrayList<>(batchSize);

            switch (type) {
                case "FeatureCollection" -> {
                    // Process FeatureCollection
                    if (!rootNode.has("features")) {
                        return Map.of("success", false, "error", "Invalid FeatureCollection: missing 'features' array");
                    }

                    JsonNode features = rootNode.get("features");
                    for (JsonNode feature : features) {
                        LocationDataRequest.LocationPoint point = convertGeoJsonFeature(feature);
                        if (point != null) {
                            batch.add(point);
                            processedCount.incrementAndGet();

                            if (batch.size() >= batchSize) {
                                sendToQueue(user, batch);
                                batch.clear();
                            }
                        }
                    }
                }
                case "Feature" -> {
                    // Process single Feature
                    LocationDataRequest.LocationPoint point = convertGeoJsonFeature(rootNode);
                    if (point != null) {
                        batch.add(point);
                        processedCount.incrementAndGet();
                    }
                }
                case "Point" -> {
                    // Process single Point geometry
                    LocationDataRequest.LocationPoint point = convertGeoJsonGeometry(rootNode, null);
                    if (point != null) {
                        batch.add(point);
                        processedCount.incrementAndGet();
                    }
                }
                case null, default -> {
                    return Map.of("success", false, "error", "Unsupported GeoJSON type: " + type + ". Only FeatureCollection, Feature, and Point are supported.");
                }
            }

            // Process any remaining locations
            if (!batch.isEmpty()) {
                sendToQueue(user, batch);
            }

            logger.info("Successfully imported and queued {} location points from GeoJSON file for user {}",
                    processedCount.get(), user.getUsername());

            return Map.of(
                    "success", true,
                    "message", "Successfully queued " + processedCount.get() + " location points for processing",
                    "pointsReceived", processedCount.get()
            );

        } catch (IOException e) {
            logger.error("Error processing GeoJSON file", e);
            return Map.of("success", false, "error", "Error processing GeoJSON file: " + e.getMessage());
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
            if (StringUtils.hasText(timeStr)) {
                point.setTimestamp(timeStr);
            } else {
                return null;
            }
        } else {
            return null;
        }
        
        // Set accuracy - GPX doesn't typically include accuracy, so use a default
        point.setAccuracyMeters(10.0); // Default accuracy of 10 meters
        
        return point;
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

    /**
     * Converts a GeoJSON Feature to our LocationPoint format
     */
    private LocationDataRequest.LocationPoint convertGeoJsonFeature(JsonNode feature) {
        if (!feature.has("geometry")) {
            return null;
        }

        JsonNode geometry = feature.get("geometry");
        JsonNode properties = feature.has("properties") ? feature.get("properties") : null;

        return convertGeoJsonGeometry(geometry, properties);
    }

    /**
     * Converts a GeoJSON geometry (Point) to our LocationPoint format
     */
    private LocationDataRequest.LocationPoint convertGeoJsonGeometry(JsonNode geometry, JsonNode properties) {
        if (!geometry.has("type") || !"Point".equals(geometry.get("type").asText())) {
            return null; // Only support Point geometries for location data
        }

        if (!geometry.has("coordinates")) {
            return null;
        }

        JsonNode coordinates = geometry.get("coordinates");
        if (!coordinates.isArray() || coordinates.size() < 2) {
            return null;
        }

        LocationDataRequest.LocationPoint point = new LocationDataRequest.LocationPoint();

        // GeoJSON coordinates are [longitude, latitude]
        double longitude = coordinates.get(0).asDouble();
        double latitude = coordinates.get(1).asDouble();

        point.setLatitude(latitude);
        point.setLongitude(longitude);

        // Try to extract timestamp from properties
        String timestamp = null;
        if (properties != null) {
            // Common timestamp field names in GeoJSON
            String[] timestampFields = {"timestamp", "time", "datetime", "date", "when"};
            for (String field : timestampFields) {
                if (properties.has(field)) {
                    timestamp = properties.get(field).asText();
                    break;
                }
            }
        }

        if (timestamp == null || timestamp.isEmpty()) {
            logger.warn("Could not determine timestamp for point {}. Will discard it", point);
            return null;
        }

        point.setTimestamp(timestamp);

        // Try to extract accuracy from properties
        Double accuracy = null;
        String[] accuracyFields = {"accuracy", "acc", "precision", "hdop"};
        for (String field : accuracyFields) {
            if (properties.has(field)) {
                accuracy = properties.get(field).asDouble();
                break;
            }
        }

        point.setAccuracyMeters(accuracy != null ? accuracy : 50.0); // Default accuracy of 50 meters

        return point;
    }

    private void sendToQueue(User user, List<LocationDataRequest.LocationPoint> batch) {
        LocationDataEvent event = new LocationDataEvent(
                user.getUsername(),
                batch
        );
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.LOCATION_DATA_ROUTING_KEY,
                event
        );
        logger.info("Queued batch of {} locations for processing", batch.size());
    }

}
