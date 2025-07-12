package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.dto.OwntracksLocationRequest;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.importer.ImportBatchProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ingest")
public class IngestApiController {

    private static final Logger logger = LoggerFactory.getLogger(IngestApiController.class);
    private static final Map<String, ? extends Serializable> SUCCESS = Map.of(
            "success", true,
            "message", "Successfully queued Owntracks location point for processing"
    );

    private final ImportBatchProcessor batchProcessor;
    private final UserJdbcService userJdbcService;

    @Autowired
    public IngestApiController(ImportBatchProcessor batchProcessor, UserJdbcService userJdbcService) {
        this.userJdbcService = userJdbcService;
        this.batchProcessor = batchProcessor;
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
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = this.userJdbcService.findByUsername(userDetails.getUsername()).orElseThrow(() -> new UsernameNotFoundException(userDetails.getUsername()));
        
        try {
            // Convert an Owntracks format to our LocationPoint format
            LocationDataRequest.LocationPoint locationPoint = request.toLocationPoint();

            if (locationPoint.getTimestamp() == null) {
                logger.warn("Ignoring location point [{}] because timestamp is null", locationPoint);
                return ResponseEntity.ok(Map.of());
            }

            this.batchProcessor.sendToQueue(user, Collections.singletonList(locationPoint));
            logger.debug("Successfully received and queued Owntracks location point for user {}",
                    user.getUsername());
            
            return ResponseEntity.accepted().body(SUCCESS);
            
        } catch (Exception e) {
            logger.error("Error processing Owntracks data", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error processing Owntracks data: " + e.getMessage()));
        }
    }
}
