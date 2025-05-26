package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.model.RawLocationPoint;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.service.ApiTokenService;
import com.dedicatedcode.reitti.service.LocationDataService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class LocationDataApiController {
    
    private static final Logger logger = LoggerFactory.getLogger(LocationDataApiController.class);
    
    private final ApiTokenService apiTokenService;
    private final LocationDataService locationDataService;
    
    @Autowired
    public LocationDataApiController(ApiTokenService apiTokenService, LocationDataService locationDataService) {
        this.apiTokenService = apiTokenService;
        this.locationDataService = locationDataService;
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
            // Process the location data
            List<RawLocationPoint> savedPoints = locationDataService.processLocationData(user, request.getPoints());
            
            logger.info("Successfully processed {} location points for user {}", 
                    savedPoints.size(), user.getUsername());
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Successfully processed " + savedPoints.size() + " location points",
                    "pointsProcessed", savedPoints.size()
            ));
            
        } catch (Exception e) {
            logger.error("Error processing location data", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error processing location data: " + e.getMessage()));
        }
    }
}
