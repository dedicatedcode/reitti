package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.service.ApiTokenService;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/v1")
public class LocationDataApiController {
    
    private static final Logger logger = LoggerFactory.getLogger(LocationDataApiController.class);
    private static final int BATCH_SIZE = 100; // Process locations in batches of 100
    
    private final ApiTokenService apiTokenService;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    
    @Autowired
    public LocationDataApiController(
            ApiTokenService apiTokenService,
            ObjectMapper objectMapper,
            RabbitTemplate rabbitTemplate) {
        this.apiTokenService = apiTokenService;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
    }
    
    @PostMapping("/location-data")
    public ResponseEntity<?> receiveLocationData(
            @RequestHeader("X-API-Token") String apiToken,
            @Valid @RequestBody LocationDataRequest request) {
        
        // Authenticate using the API token
        User user = apiTokenService.getUserByToken(apiToken)
                .orElse(null);
        
        if (user == null) {
            logger.warn("Invalid API token used: {}", apiToken);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid API token"));
        }

        try {
            // Create and publish event to RabbitMQ
            LocationDataEvent event = new LocationDataEvent(
                    user.getId(), 
                    user.getUsername(), 
                    request.getPoints()
            );
            
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.LOCATION_DATA_ROUTING_KEY,
                event
            );
            
            logger.info("Successfully received and queued {} location points for user {}", 
                    request.getPoints().size(), user.getUsername());
            
            return ResponseEntity.accepted().body(Map.of(
                    "success", true,
                    "message", "Successfully queued " + request.getPoints().size() + " location points for processing",
                    "pointsReceived", request.getPoints().size()
            ));
            
        } catch (Exception e) {
            logger.error("Error processing location data", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error processing location data: " + e.getMessage()));
        }
    }
    
    @PostMapping("/import/google-takeout")
    public ResponseEntity<?> importGoogleTakeout(
            @RequestHeader("X-API-Token") String apiToken,
            @RequestParam("file") MultipartFile file) {
        
        // Authenticate using the API token
        User user = apiTokenService.getUserByToken(apiToken)
                .orElse(null);
        
        if (user == null) {
            logger.warn("Invalid API token used: {}", apiToken);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid API token"));
        }
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }
        
        if (!file.getOriginalFilename().endsWith(".json")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only JSON files are supported"));
        }
        
        AtomicInteger processedCount = new AtomicInteger(0);
        
        try (InputStream inputStream = file.getInputStream()) {
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
                        return ResponseEntity.badRequest().body(Map.of("error", "Invalid format: 'locations' is not an array"));
                    }
                    
                    List<LocationDataRequest.LocationPoint> batch = new ArrayList<>(BATCH_SIZE);
                    
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
                                    if (batch.size() >= BATCH_SIZE) {
                                        // Create and publish event to RabbitMQ
                                        LocationDataEvent event = new LocationDataEvent(
                                                user.getId(), 
                                                user.getUsername(), 
                                                new ArrayList<>(batch) // Create a copy to avoid reference issues
                                        );
                                        
                                        rabbitTemplate.convertAndSend(
                                            RabbitMQConfig.EXCHANGE_NAME,
                                            RabbitMQConfig.LOCATION_DATA_ROUTING_KEY,
                                            event
                                        );
                                        
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
                        // Create and publish event to RabbitMQ
                        LocationDataEvent event = new LocationDataEvent(
                                user.getId(), 
                                user.getUsername(), 
                                new ArrayList<>(batch) // Create a copy to avoid reference issues
                        );
                        
                        rabbitTemplate.convertAndSend(
                            RabbitMQConfig.EXCHANGE_NAME,
                            RabbitMQConfig.LOCATION_DATA_ROUTING_KEY,
                            event
                        );
                        
                        logger.info("Queued final batch of {} locations for processing", batch.size());
                    }
                    
                    break; // We've processed the locations array, no need to continue
                }
            }
            
            logger.info("Successfully imported and queued {} location points from Google Takeout for user {}", 
                    processedCount.get(), user.getUsername());
            
            return ResponseEntity.accepted().body(Map.of(
                    "success", true,
                    "message", "Successfully queued " + processedCount.get() + " location points for processing",
                    "pointsReceived", processedCount.get()
            ));
            
        } catch (IOException e) {
            logger.error("Error processing Google Takeout file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error processing Google Takeout file: " + e.getMessage()));
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
}
