package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.LocationDataEvent;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class LocationDataProcessingService {
    private static final Logger logger = LoggerFactory.getLogger(LocationDataProcessingService.class);
    
    private final UserRepository userRepository;
    private final LocationDataService locationDataService;
    
    public LocationDataProcessingService(UserRepository userRepository, LocationDataService locationDataService) {
        this.userRepository = userRepository;
        this.locationDataService = locationDataService;
    }
    
    @RabbitListener(queues = RabbitMQConfig.LOCATION_DATA_QUEUE)
    public void handleLocationDataEvent(LocationDataEvent event) {
        logger.info("Received location data event from RabbitMQ for user {} with {} points", 
                event.getUsername(), event.getPoints().size());
        
        try {
            processLocationData(event);
        } catch (Exception e) {
            logger.error("Error processing location data event", e);
            // In a production system, you might want to implement a dead letter queue
            // for failed messages
        }
    }
    
    @Transactional
    public void processLocationData(LocationDataEvent event) {
        Optional<User> userOpt = userRepository.findById(event.getUserId());
        
        if (userOpt.isEmpty()) {
            logger.warn("User not found for ID: {}", event.getUserId());
            return;
        }
        
        User user = userOpt.get();
        locationDataService.processLocationData(user, event.getPoints());
        
        logger.info("Successfully processed {} location points for user {}", 
                event.getPoints().size(), user.getUsername());
    }
}
