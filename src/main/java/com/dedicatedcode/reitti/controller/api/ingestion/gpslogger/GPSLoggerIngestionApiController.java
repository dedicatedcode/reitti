package com.dedicatedcode.reitti.controller.api.ingestion.gpslogger;

import com.dedicatedcode.reitti.controller.api.ingestion.owntracks.OwntracksFriendResponse;
import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.dto.OwntracksLocationRequest;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.LocationBatchingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ingest")
public class GPSLoggerIngestionApiController {
    private static final Logger logger = LoggerFactory.getLogger(GPSLoggerIngestionApiController.class);
    private final UserJdbcService userJdbcService;
    private final LocationBatchingService locationBatchingService;

    public GPSLoggerIngestionApiController(UserJdbcService userJdbcService,
                                           LocationBatchingService locationBatchingService) {
        this.userJdbcService = userJdbcService;
        this.locationBatchingService = locationBatchingService;
    }

    @PostMapping("/gpslogger")
    public ResponseEntity<?> receiveData(@RequestBody OwntracksLocationRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = this.userJdbcService.findByUsername(userDetails.getUsername()).orElseThrow(() -> new UsernameNotFoundException(userDetails.getUsername()));

        try {
            if (!request.isLocationUpdate()) {
                logger.debug("Ignoring non-location GpsLogger message of type: {}", request.getType());
                // Return empty array for non-location messages
                return ResponseEntity.ok(new ArrayList<OwntracksFriendResponse>());
            }

            // Convert an Owntracks format to our LocationPoint format
            LocationPoint locationPoint = request.toLocationPoint();

            if (locationPoint.getTimestamp() == null) {
                logger.warn("Ignoring location point [{}] because timestamp is null", locationPoint);
                // Return empty array when timestamp is null
                return ResponseEntity.ok(new ArrayList<OwntracksFriendResponse>());
            }

            this.locationBatchingService.addLocationPoint(user, locationPoint);
            logger.debug("Successfully received and queued GpsLogger location point for user {}",
                         user.getUsername());

            return ResponseEntity.ok(Collections.emptyList());

        } catch (Exception e) {
            logger.error("Error processing GPSLogger data", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error processing GPSLogger data: " + e.getMessage()));
        }
    }
}
