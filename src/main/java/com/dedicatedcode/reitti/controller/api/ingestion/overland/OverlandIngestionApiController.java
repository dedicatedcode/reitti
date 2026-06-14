package com.dedicatedcode.reitti.controller.api.ingestion.overland;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.dto.OverlandLocationRequest;
import com.dedicatedcode.reitti.model.security.DeviceTokenUser;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.LocationBatchingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ingest")
public class OverlandIngestionApiController {

    private static final Logger logger = LoggerFactory.getLogger(OverlandIngestionApiController.class);

    private final UserJdbcService userJdbcService;
    private final LocationBatchingService locationBatchingService;

    @Autowired
    public OverlandIngestionApiController(UserJdbcService userJdbcService,
                                          LocationBatchingService locationBatchingService) {
        this.userJdbcService = userJdbcService;
        this.locationBatchingService = locationBatchingService;
    }

    @PostMapping("/overland")
    public ResponseEntity<?> receiveOverlandData(@AuthenticationPrincipal DeviceTokenUser user, @RequestBody OverlandLocationRequest request) {
        if (user.getDevice().isEmpty()) {
            throw new IllegalArgumentException("Token has no device attached. Please use another token or attach a device to it.");
        }

        try {
            if (request.getLocations() == null || request.getLocations().isEmpty()) {
                logger.debug("Ignoring Overland request with no locations for user {}", user.getUsername());
                return ResponseEntity.ok(Map.of(
                        "result", "ok"
                ));
            }
            
            // Convert Overland locations to our LocationPoint format
            List<LocationPoint> locationPoints = request.getLocations().stream()
                    .map(OverlandLocationRequest.OverlandLocation::toLocationPoint)
                    .filter(point -> point != null && point.getTimestamp() != null && point.getAccuracyMeters() != null)
                    .toList();
            
            if (locationPoints.isEmpty()) {
                logger.warn("No valid location points found in Overland request for user {}", user.getUsername());
                return ResponseEntity.ok(Map.of(
                        "result", "ok"
                ));
            }
            
            // Add each location point to the batching service
            for (LocationPoint point : locationPoints) {
                this.locationBatchingService.addLocationPoint(user, user.getDevice().get(), point);
            }
            logger.debug("Successfully received and queued {} Overland location points for user {}",
                    locationPoints.size(), user.getUsername());
            
            return ResponseEntity.ok(Map.of(
                    "result", "ok"
            ));
            
        } catch (Exception e) {
            logger.error("Error processing Overland data", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error processing Overland data: " + e.getMessage()));
        }
    }
}
