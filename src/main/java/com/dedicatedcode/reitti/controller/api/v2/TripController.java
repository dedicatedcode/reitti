package com.dedicatedcode.reitti.controller.api.v2;

import com.dedicatedcode.reitti.controller.api.DataAccessGuard;
import com.dedicatedcode.reitti.model.security.MagicLinkAccessLevel;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.TripApiQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v2/trips")
public class TripController {
    private final TripApiQueryService tripApiQueryService;
    private final DataAccessGuard dataAccessGuard;

    public TripController(TripApiQueryService tripApiQueryService, DataAccessGuard dataAccessGuard) {
        this.tripApiQueryService = tripApiQueryService;
        this.dataAccessGuard = dataAccessGuard;
    }

    @GetMapping("/{userId}")
    public ResponseEntity getTrips(
            @AuthenticationPrincipal User user,
            @PathVariable Long userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false, defaultValue = "UTC") String timezone) {
        User userToFetchDataFrom = dataAccessGuard.loadUserToFetchDataFrom(user, userId);
        ZoneId userTimezone = ZoneId.of(timezone);
        Optional<DataAccessGuard.TimeWindow> window = dataAccessGuard.readableWindow(user, userTimezone,
                MagicLinkAccessLevel.FULL_ACCESS, MagicLinkAccessLevel.ONLY_LIVE, MagicLinkAccessLevel.ONLY_LIVE_WITH_PHOTOS);
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
        if (window.isPresent()) {
            DataAccessGuard.TimeWindow restricted = window.get().restrict(startOfRange, endOfRange);
            startOfRange = restricted.start();
            endOfRange = restricted.end();
        }
        return ResponseEntity.ok().body(tripApiQueryService.getTrips(userToFetchDataFrom, startOfRange, endOfRange));
    }

}
