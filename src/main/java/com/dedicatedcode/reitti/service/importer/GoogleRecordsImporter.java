package com.dedicatedcode.reitti.service.importer;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.ImportStateHolder;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.jobs.JobType;
import com.dedicatedcode.reitti.service.processing.LocationPointStagingService;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
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
public class GoogleRecordsImporter {
    
    private static final Logger logger = LoggerFactory.getLogger(GoogleRecordsImporter.class);
    
    private final ObjectMapper objectMapper;
    private final ImportStateHolder stateHolder;
    private final LocationPointStagingService stagingService;
    private final Task<PromotionJobHandler.PromotionTaskData> promotionTask;
    private final JobSchedulingService jobSchedulingService;
    private final int graceTimeSeconds;

    public GoogleRecordsImporter(ObjectMapper objectMapper,
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
    
    public Map<String, Object> importGoogleRecords(InputStream inputStream, User user, Device device, String originalFilename) {
        AtomicInteger processedCount = new AtomicInteger(0);
        UUID parentJobId = null;
        String partitionKey = null;
        try {
            stateHolder.importStarted();
            logger.info("Importing Google Records file for user {}", user.getUsername());

            JsonFactory factory = objectMapper.getFactory();
            JsonParser parser = factory.createParser(inputStream);
            parentJobId = jobSchedulingService.createParentJob(
                    user,
                    JobType.GOOGLE_TIMELINE_IMPORT,
                    "Google Records Import - " + originalFilename
            );
            partitionKey = UUID.randomUUID().toString();
            stagingService.ensurePartitionExists(partitionKey);

            List<LocationPoint> batch = new ArrayList<>(stagingService.getBatchSize());
            boolean foundData = false;
            
            // Look for "locations" array (old Records.json format)
            while (parser.nextToken() != null) {
                if (parser.getCurrentToken() == JsonToken.FIELD_NAME) {
                    String fieldName = parser.currentName();
                    
                    if ("locations".equals(fieldName)) {
                        foundData = true;
                        processedCount.addAndGet(processLocationsArray(parser, batch, user, device, partitionKey));
                        break;
                    }
                }
            }
            
            if (!foundData) {
                return Map.of("success", false, "error", "Invalid format: 'locations' array not found in Records.json");
            }
            
            // Process any remaining locations
            if (!batch.isEmpty()) {
                stagingService.insertBatch(partitionKey, user, device, batch);
            }
            
            logger.info("Successfully imported and queued {} location points from Google Records for user {}", 
                    processedCount.get(), user.getUsername());
            JobSchedulingService.Metadata metadata = JobSchedulingService.Metadata.builder()
                    .user(user)
                    .jobType(JobType.GOOGLE_TIMELINE_IMPORT)
                    .friendlyName("GPS Data Promotion")
                    .build();
            jobSchedulingService.scheduleTask(promotionTask,
                                              new PromotionJobHandler.PromotionTaskData(user, device, partitionKey, true).withParentJobId(parentJobId),
                                              Instant.now().plusSeconds(graceTimeSeconds),
                                              metadata);

            return Map.of(
                    "success", true,
                    "message", "Successfully queued " + processedCount.get() + " location points for processing",
                    "pointsReceived", processedCount.get()
            );
            
        } catch (IOException e) {
            logger.error("Error processing Google Records file", e);
            if (parentJobId != null) {
                this.jobSchedulingService.cancel(parentJobId);
                this.stagingService.dropPartition(partitionKey);
            }
            return Map.of("success", false, "error", "Error processing Google Records file: " + e.getMessage());
        } finally {
            stateHolder.importFinished();
        }
    }
    
    /**
     * Processes the Records.json format with "locations" array
     */
    private int processLocationsArray(JsonParser parser, List<LocationPoint> batch, User user, Device device, String partitionKey) throws IOException {
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
                    LocationPoint point = convertGoogleRecordsLocation(locationNode);
                    if (point != null) {
                        batch.add(point);
                        processedCount++;

                        if (batch.size() >= stagingService.getBatchSize()) {
                            stagingService.insertBatch(partitionKey, user, device, batch);
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
        String timestamp = locationNode.get("timestamp").asText();
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
