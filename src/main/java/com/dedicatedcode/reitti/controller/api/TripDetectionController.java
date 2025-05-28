package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.Trip;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.UserRepository;
import com.dedicatedcode.reitti.service.processing.TripDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/trips/detection")
public class TripDetectionController {

    private static final Logger logger = LoggerFactory.getLogger(TripDetectionController.class);
    
    private final TripDetectionService tripDetectionService;
    private final UserRepository userRepository;
    
    public TripDetectionController(TripDetectionService tripDetectionService, UserRepository userRepository) {
        this.tripDetectionService = tripDetectionService;
        this.userRepository = userRepository;
    }

    
    @DeleteMapping("/clear-all")
    public ResponseEntity<?> clearAllTrips() {
        logger.info("Received request to clear all trips");
        
        tripDetectionService.clearAllTrips();
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Cleared all trips");
        
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/clear/{userId}")
    public ResponseEntity<?> clearTripsForUser(@PathVariable Long userId) {
        logger.info("Received request to clear trips for user ID: {}", userId);
        
        Optional<User> userOpt = userRepository.findById(userId);
        
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        tripDetectionService.clearTrips(userOpt.get());
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Cleared trips for user: " + userOpt.get().getUsername());
        response.put("userId", userId);
        
        return ResponseEntity.ok(response);
    }
}
