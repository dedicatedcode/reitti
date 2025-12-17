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

    @GetMapping("/processed-visits")
    public ResponseEntity<?> getProcessedVisitsForCurrentUser(@AuthenticationPrincipal User user,
                                                              @RequestParam(required = false) String date,
                                                              @RequestParam(required = false) String startDate,
                                                              @RequestParam(required = false) String endDate,
                                                              @RequestParam(required = false, defaultValue = "UTC") String timezone,
                                                              @RequestParam(required = false) Double minLat,
                                                              @RequestParam(required = false) Double maxLat,
                                                              @RequestParam(required = false) Double minLng,
                                                              @RequestParam(required = false) Double maxLng) {
        return this.getProcessedVisits(user, user.getId(), date, startDate, endDate, timezone, minLat, maxLat, minLng, maxLng);
    }

    @GetMapping("/processed-visits/{userId}")
    public ResponseEntity<?> getProcessedVisits(@AuthenticationPrincipal User user,
                                                @PathVariable Long userId,
                                                @RequestParam(required = false) String date,
                                                @RequestParam(required = false) String startDate,
                                                @RequestParam(required = false) String endDate,
                                                @RequestParam(required = false, defaultValue = "UTC") String timezone,
                                                @RequestParam(required = false) Double minLat,
                                                @RequestParam(required = false) Double maxLat,
                                                @RequestParam(required = false) Double minLng,
                                                @RequestParam(required = false) Double maxLng) {
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

            // Filter by bounding box if provided
            if (minLat != null && maxLat != null && minLng != null && maxLng != null) {
                visits = visits.stream()
                    .filter(visit -> isPlaceInBox(visit.getPlace(), minLat, maxLat, minLng, maxLng))
                    .collect(Collectors.toList());
            }

            // Group visits by place and create response
            Map<SignificantPlace, List<ProcessedVisit>> visitsByPlace = visits.stream()
                .collect(Collectors.groupingBy(ProcessedVisit::getPlace));

            List<ProcessedVisitResponse.PlaceVisitSummary> placeSummaries = visitsByPlace.entrySet().stream()
                .map(entry -> {
                    SignificantPlace place = entry.getKey();
                    List<ProcessedVisit> placeVisits = entry.getValue();
                    
                    List<ProcessedVisitResponse.VisitDetail> visitDetails = placeVisits.stream()
                        .map(ProcessedVisitResponse.VisitDetail::new)
                        .collect(Collectors.toList());
                    
                    long totalDuration = placeVisits.stream()
                        .mapToLong(ProcessedVisit::getDurationSeconds)
                        .sum();
                    
                    return new ProcessedVisitResponse.PlaceVisitSummary(
                        place, visitDetails, totalDuration, placeVisits.size());
                })
                .sorted((a, b) -> Long.compare(b.getTotalDurationSeconds(), a.getTotalDurationSeconds()))
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
}
