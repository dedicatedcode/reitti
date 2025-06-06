package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.model.RawLocationPoint;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.RawLocationPointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    
    private final RawLocationPointRepository rawLocationPointRepository;
    
    @Autowired
    public LocationDataApiController(RawLocationPointRepository rawLocationPointRepository) {
        this.rawLocationPointRepository = rawLocationPointRepository;
    }

    @GetMapping("/raw-location-points")
    public ResponseEntity<?> getRawLocationPoints(@RequestParam("date") String dateStr) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        
        try {
            // Parse the date string (expected format: YYYY-MM-DD)
            LocalDate date = LocalDate.parse(dateStr);
            
            // Create start and end instants for the day
            Instant startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            
            // Get the user from the repository
            User user = (User) userDetails;
            
            // Get raw location points for the user and date range
            List<LocationDataRequest.LocationPoint> points = rawLocationPointRepository.findByUserAndTimestampBetweenOrderByTimestampAsc(user, startOfDay, endOfDay).stream()
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
