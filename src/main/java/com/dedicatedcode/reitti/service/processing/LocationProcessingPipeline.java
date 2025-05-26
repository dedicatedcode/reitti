package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.model.RawLocationPoint;
import com.dedicatedcode.reitti.model.SignificantPlace;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.UserRepository;
import com.dedicatedcode.reitti.service.LocationDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class LocationProcessingPipeline {
    private static final Logger logger = LoggerFactory.getLogger(LocationProcessingPipeline.class);
    
    private final UserRepository userRepository;
    private final LocationDataService locationDataService;
    private final StayPointDetectionService stayPointDetectionService;
    private final SignificantPlaceService significantPlaceService;
    
    @Autowired
    public LocationProcessingPipeline(
            UserRepository userRepository,
            LocationDataService locationDataService,
            StayPointDetectionService stayPointDetectionService,
            SignificantPlaceService significantPlaceService) {
        this.userRepository = userRepository;
        this.locationDataService = locationDataService;
        this.stayPointDetectionService = stayPointDetectionService;
        this.significantPlaceService = significantPlaceService;
    }
    
    @Transactional
    public void processLocationData(LocationDataEvent event) {
        logger.info("Starting processing pipeline for user {} with {} points", 
                event.getUsername(), event.getPoints().size());
        
        Optional<User> userOpt = userRepository.findById(event.getUserId());
        
        if (userOpt.isEmpty()) {
            logger.warn("User not found for ID: {}", event.getUserId());
            return;
        }
        
        User user = userOpt.get();
        
        // Step 1: Save raw location points (with duplicate checking)
        List<RawLocationPoint> savedPoints = locationDataService.processLocationData(user, event.getPoints());
        
        if (savedPoints.isEmpty()) {
            logger.info("No new points to process for user {}", user.getUsername());
            return;
        }
        
        logger.info("Saved {} new location points for user {}", savedPoints.size(), user.getUsername());
        
        // Step 2: Detect stay points from the new data
        List<StayPoint> stayPoints = stayPointDetectionService.detectStayPoints(user, savedPoints);
        
        if (!stayPoints.isEmpty()) {
            logger.info("Detected {} stay points", stayPoints.size());
            
            // Step 3: Update significant places based on stay points
            List<SignificantPlace> updatedPlaces = significantPlaceService.processStayPoints(user, stayPoints);
            logger.info("Updated {} significant places", updatedPlaces.size());
            
            // Future steps:
            // - Detect trips between significant places
            // - Reverse geocode significant places to get addresses
        }
        
        logger.info("Completed processing pipeline for user {}", user.getUsername());
    }
}
