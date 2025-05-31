package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.dto.OwntracksLocationRequest;
import com.dedicatedcode.reitti.event.LocationDataEvent;
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

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ingest")
public class IngestApiController {
    
    private static final Logger logger = LoggerFactory.getLogger(IngestApiController.class);
    
    private final RabbitTemplate rabbitTemplate;
    
    @Autowired
    public IngestApiController(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }
    
    @PostMapping("/owntracks")
    public ResponseEntity<?> receiveOwntracksData(@RequestBody OwntracksLocationRequest request) {
        // Only process location updates
        if (!request.isLocationUpdate()) {
            logger.debug("Ignoring non-location Owntracks message of type: {}", request.getType());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Non-location update ignored"
            ));
        }
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails user = (UserDetails) authentication.getPrincipal();
        
        try {
            // Convert Owntracks format to our LocationPoint format
            LocationDataRequest.LocationPoint locationPoint = request.toLocationPoint();
            
            // Create and publish event to RabbitMQ
            LocationDataEvent event = new LocationDataEvent(
                    user.getUsername(),
                    Collections.singletonList(locationPoint)
            );
            
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.LOCATION_DATA_ROUTING_KEY,
                event
            );
            
            logger.info("Successfully received and queued Owntracks location point for user {}", 
                    user.getUsername());
            
            return ResponseEntity.accepted().body(Map.of(
                    "success", true,
                    "message", "Successfully queued Owntracks location point for processing"
            ));
            
        } catch (Exception e) {
            logger.error("Error processing Owntracks data", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error processing Owntracks data: " + e.getMessage()));
        }
    }
}
