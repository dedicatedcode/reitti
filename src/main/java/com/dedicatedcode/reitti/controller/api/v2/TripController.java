package com.dedicatedcode.reitti.controller.api.v2;

import com.dedicatedcode.reitti.model.security.MagicLinkAccessLevel;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.TripApiQueryService;
import com.dedicatedcode.reitti.service.processing.TimeRange;
import com.dedicatedcode.reitti.service.security.DataAccessGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.Map;

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
        TimeRange timeRange = dataAccessGuard.loadTimeRange(user, startDate, endDate, userTimezone);
        dataAccessGuard.hasAccessLevel(user, MagicLinkAccessLevel.FULL_ACCESS, MagicLinkAccessLevel.MEMORY_VIEW_ONLY, MagicLinkAccessLevel.MEMORY_EDIT_ACCESS);
        if (startDate == null || endDate == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "'startDate' and 'endDate' must be provided"
            ));
        }
        return ResponseEntity.ok().body(tripApiQueryService.getTrips(userToFetchDataFrom, timeRange.start(), timeRange.end()));
    }
}
