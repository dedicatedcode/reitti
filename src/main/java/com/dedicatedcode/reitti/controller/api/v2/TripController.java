package com.dedicatedcode.reitti.controller.api.v2;

import com.dedicatedcode.reitti.model.security.TokenUser;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.repository.UserSharingJdbcService;
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
import java.util.Objects;

@RestController
@RequestMapping("/api/v2/trips")
public class TripController {
    private final APIQueryService apiQueryService;
    private final UserJdbcService userJdbcService;
    private final UserSharingJdbcService userSharingJdbcService;

    public TripController(APIQueryService apiQueryService, UserJdbcService userJdbcService, UserSharingJdbcService userSharingJdbcService) {
        this.apiQueryService = apiQueryService;
        this.userJdbcService = userJdbcService;
        this.userSharingJdbcService = userSharingJdbcService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity getTrips(
            @AuthenticationPrincipal User user,
            @PathVariable Long userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false, defaultValue = "UTC") String timezone,
            @RequestParam(required = false, defaultValue = "12") Integer zoom) throws IllegalAccessException {
        User userToFetchDataFrom = loadUserToFetchDataFrom(user, userId);
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
        return ResponseEntity.ok().body(apiQueryService.getTrips(userToFetchDataFrom, startOfRange, endOfRange, zoom));
    }

    private User loadUserToFetchDataFrom(User user, Long userId) throws IllegalAccessException {
        if (user.getId().equals(userId)) {
            return user;
        }
        if (user instanceof TokenUser) {
            if (!Objects.equals(user.getId(), userId)) {
                throw new IllegalAccessException("User not allowed to fetch data for other users");
            }
        }
        if (this.userSharingJdbcService.findBySharedWithUser(user.getId()).stream().noneMatch(userSharing -> userSharing.getSharingUserId().equals(userId))) {
            throw new IllegalAccessException("User not allowed to fetch data for other user with id " + userId);
        }

        return userJdbcService.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

}
