package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.dto.ProcessedVisitResponse;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.security.MagicLinkAccessLevel;
import com.dedicatedcode.reitti.model.security.TokenUser;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class ProcessedVisitApiController {
    
    private static final Logger logger = LoggerFactory.getLogger(ProcessedVisitApiController.class);
    
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final UserJdbcService userJdbcService;
    
    @Autowired
    public ProcessedVisitApiController(ProcessedVisitJdbcService processedVisitJdbcService,
                                       UserJdbcService userJdbcService) {
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.userJdbcService = userJdbcService;
    }

    @GetMapping("/visits")
    public ResponseEntity<?> getProcessedVisitsForCurrentUser(@AuthenticationPrincipal User user,
                                                              @RequestParam(required = false) String date,
                                                              @RequestParam(required = false) String startDate,
                                                              @RequestParam(required = false) String endDate,
                                                              @RequestParam(required = false, defaultValue = "UTC") String timezone) {
        return this.getProcessedVisits(user, user.getId(), date, startDate, endDate, timezone);
    }

    @GetMapping("/visits/{userId}")
    public ResponseEntity<?> getProcessedVisits(@AuthenticationPrincipal User user,
                                                @PathVariable Long userId,
                                                @RequestParam(required = false) String date,
                                                @RequestParam(required = false) String startDate,
                                                @RequestParam(required = false) String endDate,
                                                @RequestParam(required = false, defaultValue = "UTC") String timezone) {
        try {
            ZoneId userTimezone = ZoneId.of(timezone);
            Instant startOfRange = null;
            Instant endOfRange = null;

            // Support both single date and date range
            if (startDate != null && endDate != null) {
                // First try to parse them as date time
                try {
                    LocalDateTime startTimestamp = LocalDateTime.parse(startDate);
                    LocalDateTime endTimestamp = LocalDateTime.parse(endDate);
                    startOfRange = startTimestamp.atZone(userTimezone).toInstant();
                    endOfRange = endTimestamp.atZone(userTimezone).toInstant();
                } catch (DateTimeParseException ignored) {
                }

                if (startOfRange == null && endOfRange == null) {
                    LocalDate selectedStartDate = LocalDate.parse(startDate);
                    LocalDate selectedEndDate = LocalDate.parse(endDate);
                    startOfRange = selectedStartDate.atStartOfDay(userTimezone).toInstant();
                    endOfRange = selectedEndDate.plusDays(1).atStartOfDay(userTimezone).toInstant().minusMillis(1);
                }
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

            // Check access permissions
            boolean hasAccess = true;
            if (user instanceof TokenUser) {
                if (!Objects.equals(user.getId(), userId)) {
                    throw new IllegalAccessException("User not allowed to fetch processed visits for other users");
                }

                hasAccess = user.getAuthorities().stream().anyMatch(a ->
                        a.equals(MagicLinkAccessLevel.FULL_ACCESS.asAuthority()) ||
                        a.equals(MagicLinkAccessLevel.ONLY_LIVE.asAuthority()) ||
                        a.equals(MagicLinkAccessLevel.ONLY_LIVE_WITH_PHOTOS.asAuthority()));
            }

            if (!hasAccess) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Insufficient permissions to access processed visits"));
            }

            // Get the user from the repository by userId
            User userToFetchDataFrom = userJdbcService.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

            // Fetch processed visits in the time range
            List<ProcessedVisit> visits = processedVisitJdbcService.findByUserAndTimeOverlap(
                userToFetchDataFrom, startOfRange, endOfRange);

            // Group visits by place and create response
            Map<SignificantPlace, List<ProcessedVisit>> visitsByPlace = visits.stream()
                .collect(Collectors.groupingBy(ProcessedVisit::getPlace));

            List<ProcessedVisitResponse.PlaceVisitSummary> placeSummaries = visitsByPlace.entrySet().stream()
                .map(entry -> {
                    SignificantPlace place = entry.getKey();
                    List<ProcessedVisit> placeVisits = entry.getValue();
                    
                    // Create PlaceInfo DTO
                    ProcessedVisitResponse.PlaceInfo placeInfo = new ProcessedVisitResponse.PlaceInfo(
                        place.getId(),
                        place.getName(),
                        place.getAddress(),
                        place.getCity(),
                        place.getCountryCode(),
                        place.getLatitudeCentroid(),
                        place.getLongitudeCentroid(),
                        place.getType() != null ? place.getType().toString() : null,
                        place.getPolygon()
                    );
                    
                    // Create VisitDetail DTOs
                    List<ProcessedVisitResponse.VisitDetail> visitDetails = placeVisits.stream()
                        .map(visit -> new ProcessedVisitResponse.VisitDetail(
                            visit.getId(),
                            visit.getStartTime().toString(),
                            visit.getEndTime().toString(),
                            visit.getDurationSeconds()
                        ))
                        .collect(Collectors.toList());
                    
                    long totalDurationMs = placeVisits.stream()
                        .mapToLong(ProcessedVisit::getDurationSeconds)
                        .sum() * 1000; // Convert to milliseconds
                    
                    // Generate a color for the place (you might want to implement a proper color generation strategy)
                    String color = generateColorForPlace(place);
                    
                    return new ProcessedVisitResponse.PlaceVisitSummary(
                        placeInfo, visitDetails, totalDurationMs, placeVisits.size(), color);
                })
                .sorted((a, b) -> Long.compare(b.getTotalDurationMs(), a.getTotalDurationMs()))
                .collect(Collectors.toList());

            return ResponseEntity.ok(new ProcessedVisitResponse(placeSummaries));
            
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Invalid date format. Expected format: YYYY-MM-DD or YYYY-MM-DDTHH:MM:SS"
            ));
        } catch (Exception e) {
            logger.error("Error fetching processed visits", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error fetching processed visits: " + e.getMessage()));
        }
    }

    private boolean isPlaceInBox(SignificantPlace place, Double minLat, Double maxLat, Double minLng, Double maxLng) {
        if (place == null || place.getLatitudeCentroid() == null || place.getLongitudeCentroid() == null) {
            return false;
        }
        
        return place.getLatitudeCentroid() >= minLat &&
               place.getLatitudeCentroid() <= maxLat &&
               place.getLongitudeCentroid() >= minLng &&
               place.getLongitudeCentroid() <= maxLng;
    }
    
    private String generateColorForPlace(SignificantPlace place) {
        // Simple color generation based on place ID
        // You can implement a more sophisticated color generation strategy
        if (place.getId() == null) {
            return "#3388ff";
        }
        
        // Generate a color based on the place ID
        int hash = place.getId().hashCode();
        int r = (hash & 0xFF0000) >> 16;
        int g = (hash & 0x00FF00) >> 8;
        int b = hash & 0x0000FF;
        
        // Ensure colors are not too dark
        r = Math.max(r, 100);
        g = Math.max(g, 100);
        b = Math.max(b, 100);
        
        return String.format("#%02x%02x%02x", r, g, b);
    }
}
