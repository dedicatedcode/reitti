package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.ApiToken;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.LocationPointsSimplificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class LocationDataApiController {
    
    private static final Logger logger = LoggerFactory.getLogger(LocationDataApiController.class);
    
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final LocationPointsSimplificationService simplificationService;
    private final UserJdbcService userJdbcService;
    
    @Autowired
    public LocationDataApiController(RawLocationPointJdbcService rawLocationPointJdbcService,
                                     LocationPointsSimplificationService simplificationService,
                                     UserJdbcService userJdbcService) {
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.simplificationService = simplificationService;
        this.userJdbcService = userJdbcService;
    }

    @GetMapping("/raw-location-points")
    public ResponseEntity<?> getRawLocationPointsForCurrentUser(@AuthenticationPrincipal User user,
                                                                @RequestParam(required = false) String date,
                                                                @RequestParam(required = false) String startDate,
                                                                @RequestParam(required = false) String endDate,
                                                                @RequestParam(required = false, defaultValue = "UTC") String timezone,
                                                                @RequestParam(required = false) Integer zoom) {
        return this.getRawLocationPoints(user.getId(), date, startDate, endDate, timezone, zoom);
    }

    @GetMapping("/raw-location-points/{userId}")
    public ResponseEntity<?> getRawLocationPoints(@PathVariable Long userId,
                                                  @RequestParam(required = false) String date,
                                                  @RequestParam(required = false) String startDate,
                                                  @RequestParam(required = false) String endDate,
                                                  @RequestParam(required = false, defaultValue = "UTC") String timezone,
                                                  @RequestParam(required = false) Integer zoom) {
        try {
            ZoneId userTimezone = ZoneId.of(timezone);
            Instant startOfRange;
            Instant endOfRange;

            // Support both single date and date range
            if (startDate != null && endDate != null) {
                // Date range mode
                LocalDate selectedStartDate = LocalDate.parse(startDate);
                LocalDate selectedEndDate = LocalDate.parse(endDate);

                startOfRange = selectedStartDate.atStartOfDay(userTimezone).toInstant();
                endOfRange = selectedEndDate.plusDays(1).atStartOfDay(userTimezone).toInstant().minusMillis(1);
            } else if (date != null) {
                // Single date mode (backward compatibility)
                LocalDate selectedDate = LocalDate.parse(date);

                startOfRange = selectedDate.atStartOfDay(userTimezone).toInstant();
                endOfRange = selectedDate.plusDays(1).atStartOfDay(userTimezone).toInstant().minusMillis(1);
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Either 'date' or both 'startDate' and 'endDate' must be provided"
                ));
            }

            // Get the user from the repository by userId
            User user = userJdbcService.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Get raw location points for the user and date range
            List<LocationDataRequest.LocationPoint> points = rawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(user, startOfRange, endOfRange).stream()
                .filter(point -> !point.getTimestamp().isBefore(startOfRange) && point.getTimestamp().isBefore(endOfRange))
                .sorted(Comparator.comparing(RawLocationPoint::getTimestamp))
                    .map(point -> {
                        LocationDataRequest.LocationPoint p = new LocationDataRequest.LocationPoint();
                        p.setLatitude(point.getLatitude());
                        p.setLongitude(point.getLongitude());
                        p.setAccuracyMeters(point.getAccuracyMeters());
                        p.setTimestamp(point.getTimestamp().toString());
                        return p;
                    })
                    .toList();

            List<LocationDataRequest.LocationPoint> simplifiedPoints = simplificationService.simplifyPoints(points, zoom);

            return ResponseEntity.ok(Map.of("points", simplifiedPoints));
            
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

    @GetMapping("/latest-location")
    public ResponseEntity<?> getLatestLocationForCurrentUser(@AuthenticationPrincipal User user,
                                                             @RequestParam(required = false) Instant since) {
        try {
            Optional<RawLocationPoint> latest;
            if (since == null) {
                latest = rawLocationPointJdbcService.findLatest(user);
            } else {
                latest = rawLocationPointJdbcService.findLatest(user, since);
            }

            if (latest.isEmpty()) {
                return ResponseEntity.ok(Map.of("hasLocation", false));
            }
            
            RawLocationPoint latestPoint = latest.get();
            
            LocationDataRequest.LocationPoint point = new LocationDataRequest.LocationPoint();
            point.setLatitude(latestPoint.getLatitude());
            point.setLongitude(latestPoint.getLongitude());
            point.setAccuracyMeters(latestPoint.getAccuracyMeters());
            point.setTimestamp(latestPoint.getTimestamp().toString());
            
            return ResponseEntity.ok(Map.of(
                "hasLocation", true,
                "point", point
            ));
            
        } catch (Exception e) {
            logger.error("Error fetching latest location", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error fetching latest location: " + e.getMessage()));
        }
    }
}
