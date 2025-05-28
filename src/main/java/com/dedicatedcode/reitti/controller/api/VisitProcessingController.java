package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.ProcessedVisit;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.UserRepository;
import com.dedicatedcode.reitti.service.processing.VisitMergingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/visits/processing")
public class VisitProcessingController {

    private static final Logger logger = LoggerFactory.getLogger(VisitProcessingController.class);
    
    private final VisitMergingService visitMergingService;
    private final UserRepository userRepository;
    
    public VisitProcessingController(VisitMergingService visitMergingService, UserRepository userRepository) {
        this.visitMergingService = visitMergingService;
        this.userRepository = userRepository;
    }

    @DeleteMapping("/clear-all")
    public ResponseEntity<?> clearAllProcessedVisits() {
        logger.info("Received request to clear all processed visits");
        
        visitMergingService.clearAllProcessedVisits();
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Cleared all processed visits");
        
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/clear/{userId}")
    public ResponseEntity<?> clearProcessedVisitsForUser(@PathVariable Long userId) {
        logger.info("Received request to clear processed visits for user ID: {}", userId);
        
        Optional<User> userOpt = userRepository.findById(userId);
        
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        visitMergingService.clearProcessedVisits(userOpt.get());
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Cleared processed visits for user: " + userOpt.get().getUsername());
        response.put("userId", userId);
        
        return ResponseEntity.ok(response);
    }
}
