package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.model.RawLocationPoint;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.service.ImportHandler;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class LocationDataApiController {
    
    private static final Logger logger = LoggerFactory.getLogger(LocationDataApiController.class);
    
    private final RabbitTemplate rabbitTemplate;
    private final ImportHandler importHandler;
    
    @Autowired
    public LocationDataApiController(
            RabbitTemplate rabbitTemplate,
            ImportHandler importHandler) {
        this.rabbitTemplate = rabbitTemplate;
        this.importHandler = importHandler;
    }
    
    @PostMapping("/location-data")
    public ResponseEntity<?> receiveLocationData(
            @Valid @RequestBody LocationDataRequest request) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails user = (UserDetails) authentication.getPrincipal();

        try {
            // Create and publish event to RabbitMQ
            LocationDataEvent event = new LocationDataEvent(
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
            @RequestParam("file") MultipartFile file) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails user = (UserDetails) authentication.getPrincipal();
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "File is empty"));
        }
        
        if (!file.getOriginalFilename().endsWith(".json")) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Only JSON files are supported"));
        }
        
        try (InputStream inputStream = file.getInputStream()) {
            Map<String, Object> result = importHandler.importGoogleTakeout(inputStream, user.getUsername());
        
            if ((Boolean) result.get("success")) {
                return ResponseEntity.accepted().body(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }
        } catch (IOException e) {
            logger.error("Error processing Google Takeout file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Error processing file: " + e.getMessage()));
        }
    }
    
    @PostMapping("/import/gpx")
    public ResponseEntity<?> importGpx(
            @RequestParam("file") MultipartFile file) {
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails user = (UserDetails) authentication.getPrincipal();
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "File is empty"));
        }
        
        if (!file.getOriginalFilename().endsWith(".gpx")) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Only GPX files are supported"));
        }
        
        try (InputStream inputStream = file.getInputStream()) {
            Map<String, Object> result = importHandler.importGpx(inputStream, user.getUsername());
            
            if ((Boolean) result.get("success")) {
                return ResponseEntity.accepted().body(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }
        } catch (IOException e) {
            logger.error("Error processing GPX file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Error processing file: " + e.getMessage()));
        }
    }
    
    @GetMapping("/raw-location-points")
    public ResponseEntity<?> getRawLocationPoints(@RequestParam("date") String dateStr) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        
        try {
            // Parse the date string (expected format: YYYY-MM-DD)
            LocalDate date = LocalDate.parse(dateStr);
            
            // Create start and end instants for the day
            Instant startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            
            // Get the user from the repository
            User user = (User) userDetails;
            
            // Get raw location points for the user and date range
            List<RawLocationPoint> rawPoints = user.getLocationPoints().stream()
                .filter(point -> !point.getTimestamp().isBefore(startOfDay) && point.getTimestamp().isBefore(endOfDay))
                .sorted(Comparator.comparing(RawLocationPoint::getTimestamp))
                .collect(Collectors.toList());
            
            // Convert to DTO objects
            List<Map<String, Object>> pointDtos = rawPoints.stream()
                .map(point -> {
                    Map<String, Object> dto = new HashMap<>();
                    dto.put("latitude", point.getLatitude());
                    dto.put("longitude", point.getLongitude());
                    dto.put("timestamp", point.getTimestamp().toString());
                    dto.put("accuracyMeters", point.getAccuracyMeters());
                    return dto;
                })
                .collect(Collectors.toList());
            
            return ResponseEntity.ok(Map.of("points", pointDtos));
            
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Invalid date format. Expected format: YYYY-MM-DD"
            ));
        } catch (Exception e) {
            logger.error("Error fetching raw location points", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error fetching raw location points: " + e.getMessage()));
        }
    }
}
