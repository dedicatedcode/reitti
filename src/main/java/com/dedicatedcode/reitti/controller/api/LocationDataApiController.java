package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.model.RawLocationPoint;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class LocationDataApiController {
    
    private static final Logger logger = LoggerFactory.getLogger(LocationDataApiController.class);
    
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final UserJdbcService userJdbcService;
    
    @Autowired
    public LocationDataApiController(RawLocationPointJdbcService rawLocationPointJdbcService,
                                   UserJdbcService userJdbcService) {
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.userJdbcService = userJdbcService;
    }

    @GetMapping("/raw-location-points/{userId}")
    public ResponseEntity<?> getRawLocationPoints(@PathVariable Long userId,
                                                  @RequestParam("date") String dateStr,
                                                  @RequestParam(required = false, defaultValue = "UTC") String timezone) {
        try {
            LocalDate date = LocalDate.parse(dateStr);
            ZoneId userTimezone = ZoneId.of(timezone);

            // Convert LocalDate to start and end Instant for the selected date in user's timezone
            Instant startOfDay = date.atStartOfDay(userTimezone).toInstant();
            Instant endOfDay = date.plusDays(1).atStartOfDay(userTimezone).toInstant().minusMillis(1);

            // Get the user from the repository by userId
            User user = userJdbcService.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Get raw location points for the user and date range
            List<LocationDataRequest.LocationPoint> points = rawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(user, startOfDay, endOfDay).stream()
                .filter(point -> !point.getTimestamp().isBefore(startOfDay) && point.getTimestamp().isBefore(endOfDay))
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

            return ResponseEntity.ok(Map.of("points", points));
            
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
}
