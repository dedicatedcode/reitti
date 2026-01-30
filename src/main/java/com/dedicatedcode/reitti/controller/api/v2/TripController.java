package com.dedicatedcode.reitti.controller.api.v2;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.APIQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/trips")
public class TripController {
    private final APIQueryService apiQueryService;

    public TripController(APIQueryService apiQueryService) {
        this.apiQueryService = apiQueryService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity getTrips(
            @AuthenticationPrincipal User user,
            @PathVariable Long userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false, defaultValue = "UTC") String timezone,
            @RequestParam(required = false) Integer zoom) {
        ZoneId userTimezone = ZoneId.of(timezone);
        Instant startOfRange = null;
        Instant endOfRange = null;

        // Support both single date and date range
        if (startDate != null && endDate != null) {
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
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Either 'date' or both 'startDate' and 'endDate' must be provided"
            ));
        }
        return ResponseEntity.ok().body(apiQueryService.getTrips(user, startOfRange, endOfRange, zoom));
    }

}
