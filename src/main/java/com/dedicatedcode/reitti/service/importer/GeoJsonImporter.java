package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.model.User;
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
public class GeoJsonImporter {
    
    private static final Logger logger = LoggerFactory.getLogger(GeoJsonImporter.class);
    
    private final ObjectMapper objectMapper;
    private final ImportBatchProcessor batchProcessor;
    
    public GeoJsonImporter(ObjectMapper objectMapper, ImportBatchProcessor batchProcessor) {
        this.objectMapper = objectMapper;
        this.batchProcessor = batchProcessor;
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
            List<LocationDataRequest.LocationPoint> batch = new ArrayList<>(batchProcessor.getBatchSize());

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

                            if (batch.size() >= batchProcessor.getBatchSize()) {
                                batchProcessor.sendToQueue(user, batch);
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
                batchProcessor.sendToQueue(user, batch);
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
}
