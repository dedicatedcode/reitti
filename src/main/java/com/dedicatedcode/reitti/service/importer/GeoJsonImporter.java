package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.ImportStateHolder;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.jobs.JobType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kagkarlsson.scheduler.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class GeoJsonImporter {
    
    private static final Logger logger = LoggerFactory.getLogger(GeoJsonImporter.class);
    
    private final ObjectMapper objectMapper;
    private final ImportStateHolder stateHolder;
    private final LocationPointStagingService stagingService;
    private final Task<PromotionJobHandler.PromotionTaskData> promotionTask;
    private final JobSchedulingService jobSchedulingService;
    private final int graceTimeSeconds;

    public GeoJsonImporter(ObjectMapper objectMapper,
                           ImportStateHolder stateHolder,
                           LocationPointStagingService stagingService,
                           Task<PromotionJobHandler.PromotionTaskData> promotionTask,
                           JobSchedulingService jobSchedulingService,
                           @Value("${reitti.import.grace-time-seconds:300}") int graceTimeSeconds) {
        this.objectMapper = objectMapper;
        this.stateHolder = stateHolder;
        this.stagingService = stagingService;
        this.promotionTask = promotionTask;
        this.jobSchedulingService = jobSchedulingService;
        this.graceTimeSeconds = graceTimeSeconds;
    }
    
    public Map<String, Object> importGeoJson(InputStream inputStream, User user, Device device, String originalFilename) {
        AtomicInteger processedCount = new AtomicInteger(0);

        try {
            stateHolder.importStarted();
            logger.info("Importing GeoJSON file for user {}", user.getUsername());
            JsonNode rootNode = objectMapper.readTree(inputStream);

            // Check if it's a valid GeoJSON
            if (!rootNode.has("type")) {
                return Map.of("success", false, "error", "Invalid GeoJSON: missing 'type' field");
            }
            String partitionKey = UUID.randomUUID().toString();
            this.stagingService.ensurePartitionExists(partitionKey);
            UUID parentJobId = jobSchedulingService.createParentJob(
                    user,
                    JobType.GEOJSON_IMPORT,
                    "GeoJson Import - " + originalFilename
            );
            String type = rootNode.get("type").asText();
            List<LocationPoint> batch = new ArrayList<>(stagingService.getBatchSize());

            switch (type) {
                case "FeatureCollection" -> {
                    // Process FeatureCollection
                    if (!rootNode.has("features")) {
                        return Map.of("success", false, "error", "Invalid FeatureCollection: missing 'features' array");
                    }

                    JsonNode features = rootNode.get("features");
                    for (JsonNode feature : features) {
                        LocationPoint point = convertGeoJsonFeature(feature);
                        if (point != null) {
                            batch.add(point);
                            processedCount.incrementAndGet();

                            if (batch.size() >= stagingService.getBatchSize()) {
                                stagingService.insertBatch(partitionKey, user, device, batch);
                                batch.clear();
                            }
                        }
                    }
                }
                case "Feature" -> {
                    // Process single Feature
                    LocationPoint point = convertGeoJsonFeature(rootNode);
                    if (point != null) {
                        batch.add(point);
                        processedCount.incrementAndGet();
                    }
                }
                case "Point" -> {
                    // Process single Point geometry
                    LocationPoint point = convertGeoJsonGeometry(rootNode, null);
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
                stagingService.insertBatch(partitionKey, user, device, batch);
            }


            logger.info("Imported and queued {} location points from GeoJSON file for user [{}]", processedCount.get(), user.getUsername());

            JobSchedulingService.Metadata metadata = JobSchedulingService.Metadata.builder()
                    .user(user)
                    .jobType(JobType.GOOGLE_TIMELINE_IMPORT)
                    .friendlyName("GeoJson Data Promotion")
                    .parentId(parentJobId)
                    .build();
            jobSchedulingService.scheduleTask(promotionTask,
                                              new PromotionJobHandler.PromotionTaskData(user, device, partitionKey, true, parentJobId),
                                              Instant.now().plusSeconds(graceTimeSeconds),
                                              metadata);
            if (processedCount.get() == 0) {
                return Map.of("success", false,
                        "error", "No valid location points found in GeoJSON",
                        "pointsReceived", 0);
            } else {
                return Map.of(
                        "success", true,
                        "message", "Successfully queued " + processedCount.get() + " location points for processing",
                        "pointsReceived", processedCount.get()
                );
            }
        } catch (IOException e) {
            logger.error("Error processing GeoJSON file", e);
            return Map.of("success", false, "error", "Error processing GeoJSON file: " + e.getMessage());
        } finally {
            stateHolder.importFinished();
        }
    }
    
    /**
     * Converts a GeoJSON Feature to our LocationPoint format
     */
    private LocationPoint convertGeoJsonFeature(JsonNode feature) {
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
    private LocationPoint convertGeoJsonGeometry(JsonNode geometry, JsonNode properties) {
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

        LocationPoint point = new LocationPoint();

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

        // Convert Unix epoch timestamp to ISO format if needed
        Instant isoTimestamp = convertToIsoTimestamp(timestamp);
        if (isoTimestamp == null) {
            logger.warn("Could not parse timestamp '{}' for point {}. Will discard it", timestamp, point);
            return null;
        }

        point.setTimestamp(isoTimestamp);

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

        // Try to extract elevation from coordinates (3rd element) or properties
        Double elevation = null;
        
        // First try coordinates array (GeoJSON can have [lon, lat, elevation])
        if (coordinates.size() >= 3) {
            try {
                elevation = coordinates.get(2).asDouble();
            } catch (Exception e) {
                // Ignore invalid elevation in coordinates
            }
        }
        
        // If not found in coordinates, try properties
        if (elevation == null) {
            String[] elevationFields = {"elevation", "ele", "altitude", "alt", "height"};
            for (String field : elevationFields) {
                if (properties.has(field)) {
                    try {
                        elevation = properties.get(field).asDouble();
                        break;
                    } catch (Exception e) {
                        // Ignore invalid elevation values
                    }
                }
            }
        }
        
        point.setElevationMeters(elevation);

        return point;
    }

    /**
     * Converts timestamp to ISO format. Handles both Unix epoch seconds and ISO strings.
     */
    private Instant convertToIsoTimestamp(String timestamp) {
        try {
            long epochSeconds = Long.parseLong(timestamp);
            return Instant.ofEpochSecond(epochSeconds);
        } catch (NumberFormatException e) {
            try {
                return ZonedDateTime.parse(timestamp).withZoneSameInstant(java.time.ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toInstant();
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
