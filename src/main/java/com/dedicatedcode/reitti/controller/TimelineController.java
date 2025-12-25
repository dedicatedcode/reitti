package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.dto.TimelineData;
import com.dedicatedcode.reitti.dto.TimelineEntry;
import com.dedicatedcode.reitti.dto.UserTimelineData;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSettings;
import com.dedicatedcode.reitti.repository.*;
import com.dedicatedcode.reitti.service.AvatarService;
import com.dedicatedcode.reitti.service.TimelineService;
import com.dedicatedcode.reitti.service.integration.ReittiIntegrationService;
import com.dedicatedcode.reitti.service.processing.TransportModeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Controller
@RequestMapping("/timeline")
public class TimelineController {

    private final SignificantPlaceJdbcService placeService;
    private final SignificantPlaceOverrideJdbcService placeOverrideJdbcService;
    private final UserJdbcService userJdbcService;

    private final AvatarService avatarService;
    private final ReittiIntegrationService reittiIntegrationService;
    private final UserSharingJdbcService userSharingJdbcService;
    private final TimelineService timelineService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final TransportModeService transportModeService;
    private final TripJdbcService tripJdbcService;

    @Autowired
    public TimelineController(SignificantPlaceJdbcService placeService, SignificantPlaceOverrideJdbcService placeOverrideJdbcService,
                              UserJdbcService userJdbcService,
                              AvatarService avatarService,
                              ReittiIntegrationService reittiIntegrationService, UserSharingJdbcService userSharingJdbcService,
                              TimelineService timelineService,
                              UserSettingsJdbcService userSettingsJdbcService,
                              TransportModeService transportModeService,
                              TripJdbcService tripJdbcService) {
        this.placeService = placeService;
        this.placeOverrideJdbcService = placeOverrideJdbcService;
        this.userJdbcService = userJdbcService;
        this.avatarService = avatarService;
        this.reittiIntegrationService = reittiIntegrationService;
        this.userSharingJdbcService = userSharingJdbcService;
        this.timelineService = timelineService;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.transportModeService = transportModeService;
        this.tripJdbcService = tripJdbcService;
    }

    @GetMapping("/content/range")
    public String getTimelineContentRange(@RequestParam LocalDate startDate,
                                          @RequestParam LocalDate endDate,
                                          @RequestParam(required = false, defaultValue = "UTC") String timezone,
                                          Authentication principal, Model model) {
        return getTimelineContentRange(startDate, endDate, timezone, principal, model, null);
    }

    @GetMapping("/trips/edit-form/{id}")
    public String getTripEditForm(@PathVariable Long id,
                                  Model model) {
        Trip trip = tripJdbcService.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("tripId", id);
        model.addAttribute("transportMode", trip.getTransportModeInferred());
        model.addAttribute("availableTransportModes", Arrays.stream(TransportMode.values()).filter(t -> t != TransportMode.UNKNOWN).toList());
        return "fragments/trip-edit :: edit-form";
    }

    @PutMapping("/trips/{id}/transport-mode")
    public String updateTripTransportMode(@PathVariable Long id,
                                          @RequestParam String transportMode,
                                          Authentication principal,
                                          Model model) {
        // Find the user by username
        User user = userJdbcService.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Trip trip = tripJdbcService.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        try {
            TransportMode mode = TransportMode.valueOf(transportMode);
            tripJdbcService.update(trip.withTransportMode(mode));
            transportModeService.overrideTransportMode(user, mode, trip);
            
            model.addAttribute("tripId", id);
            model.addAttribute("transportMode", mode);
            return "fragments/trip-edit :: view-mode";
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid transport mode");
        }
    }

    @GetMapping("/trips/view/{id}")
    public String getTripView(@PathVariable Long id, Model model) {
        Trip trip = tripJdbcService.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("tripId", id);
        model.addAttribute("transportMode", trip.getTransportModeInferred());
        model.addAttribute("availableTransportModes", Arrays.stream(TransportMode.values()).filter(t -> t != TransportMode.UNKNOWN).toList());
        return "fragments/trip-edit :: view-mode";
    }

    private String getTimelineContent(String date,
                                      String timezone,
                                      Authentication principal, Model model,
                                      Long selectedPlaceId) {

        List<String> authorities = principal.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();

        LocalDate selectedDate = LocalDate.parse(date);
        ZoneId userTimezone = ZoneId.of(timezone);
        LocalDate now = LocalDate.now(userTimezone);

        if (!selectedDate.isEqual(now)) {
            if (!authorities.contains("ROLE_USER") && !authorities.contains("ROLE_ADMIN") && !authorities.contains("ROLE_MAGIC_LINK_FULL_ACCESS")) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
        }

        // Find the user by username
        User user = userJdbcService.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Convert LocalDate to start and end Instant for the selected date in user's timezone
        Instant startOfDay = selectedDate.atStartOfDay(userTimezone).toInstant();
        Instant endOfDay = selectedDate.plusDays(1).atStartOfDay(userTimezone).toInstant().minusMillis(1);

        // Build timeline data for current user and connected users
        List<UserTimelineData> allUsersData = new ArrayList<>();

        // Add current user data first
        List<TimelineEntry> currentUserEntries = this.timelineService.buildTimelineEntries(user, userTimezone, selectedDate, startOfDay, endOfDay);

        String currentUserRawLocationPointsUrl = String.format("/api/v1/raw-location-points/%d?date=%s&timezone=%s", user.getId(), date, timezone);
        String currentUserProcessedVisitsUrl = String.format("/api/v1/visits/%d?date=%s&timezone=%s", user.getId(), date, timezone);
        String currentUserAvatarUrl = this.avatarService.getInfo(user.getId()).map(avatarInfo -> String.format("/avatars/%d?ts=%s", user.getId(), avatarInfo.updatedAt())).orElse(String.format("/avatars/%d", user.getId()));
        String currentUserInitials = this.avatarService.generateInitials(user.getDisplayName());
        allUsersData.add(new UserTimelineData(user.getId() + "", user.getDisplayName(), currentUserInitials, currentUserAvatarUrl, null, currentUserEntries, currentUserRawLocationPointsUrl, currentUserProcessedVisitsUrl));

        if (authorities.contains("ROLE_USER") || authorities.contains("ROLE_ADMIN")) {
            allUsersData.addAll(this.reittiIntegrationService.getTimelineData(user, selectedDate, userTimezone));
            allUsersData.addAll(handleSharedUserData(user, selectedDate, userTimezone, startOfDay, endOfDay));
        }
        TimelineData timelineData = new TimelineData(allUsersData.stream().filter(Objects::nonNull).toList());

        model.addAttribute("timelineData", timelineData);
        model.addAttribute("selectedPlaceId", selectedPlaceId);
        model.addAttribute("data", selectedDate);
        model.addAttribute("timezone", timezone);
        model.addAttribute("timeDisplayMode", userSettingsJdbcService.getOrCreateDefaultSettings(user.getId()).getTimeDisplayMode());
        return "fragments/timeline :: timeline-content";
    }

    private String getTimelineContentRange(LocalDate startDate,
                                           LocalDate endDate,
                                           String timezone,
                                           Authentication principal, Model model,
                                           Long selectedPlaceId) {

        List<String> authorities = principal.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();

        LocalDate selectedStartDate = startDate;
        LocalDate selectedEndDate = endDate;
        ZoneId userTimezone = ZoneId.of(timezone);
        LocalDate now = LocalDate.now(userTimezone);

        // Check if any date in the range is not today
        if (!selectedStartDate.isEqual(now) || !selectedEndDate.isEqual(now)) {
            if (!authorities.contains("ROLE_USER") && !authorities.contains("ROLE_ADMIN") && !authorities.contains("ROLE_MAGIC_LINK_FULL_ACCESS")) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
        }

        // Find the user by username
        User user = userJdbcService.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        UserSettings userSettings = userSettingsJdbcService.getOrCreateDefaultSettings(user.getId());

        // Convert LocalDate to start and end Instant for the date range in user's timezone
        Instant startOfRange = selectedStartDate.atStartOfDay(userTimezone).toInstant();
        Instant endOfRange = selectedEndDate.plusDays(1).atStartOfDay(userTimezone).toInstant().minusMillis(1);

        // Build timeline data for current user and connected users
        List<UserTimelineData> allUsersData = new ArrayList<>();

        // Add current user data first - for range, we'll use the start date for the timeline service
        List<TimelineEntry> currentUserEntries;
        if (authorities.contains("ROLE_USER") || authorities.contains("ROLE_ADMIN") || authorities.contains("ROLE_MAGIC_LINK_FULL_ACCESS")) {
            currentUserEntries = this.timelineService.buildTimelineEntries(user, userTimezone, selectedStartDate, startOfRange, endOfRange);
        } else {
            currentUserEntries = Collections.emptyList();
        }

        String currentUserRawLocationPointsUrl = String.format("/api/v1/raw-location-points/%d?startDate=%s&endDate=%s&timezone=%s", user.getId(), selectedStartDate, selectedEndDate, timezone);
        String currentUserProcessedVisitsUrl = String.format("/api/v1/visits/%d?startDate=%s&endDate=%s&timezone=%s", user.getId(), selectedStartDate, selectedEndDate, timezone);
        String currentUserAvatarUrl = this.avatarService.getInfo(user.getId()).map(avatarInfo -> String.format("/avatars/%d?ts=%s", user.getId(), avatarInfo.updatedAt())).orElse(String.format("/avatars/%d", user.getId()));
        String currentUserInitials = this.avatarService.generateInitials(user.getDisplayName());
        allUsersData.add(new UserTimelineData(user.getId() + "", user.getDisplayName(), currentUserInitials, currentUserAvatarUrl, userSettings.getColor(), currentUserEntries, currentUserRawLocationPointsUrl, currentUserProcessedVisitsUrl));

        if (authorities.contains("ROLE_USER") || authorities.contains("ROLE_ADMIN")) {
            allUsersData.addAll(this.reittiIntegrationService.getTimelineDataRange(user, selectedStartDate, selectedEndDate, userTimezone));
            allUsersData.addAll(handleSharedUserDataRange(user, selectedStartDate, selectedEndDate, userTimezone));
        }
        
        TimelineData timelineData = new TimelineData(allUsersData.stream().filter(Objects::nonNull).toList());

        model.addAttribute("timelineData", timelineData);
        model.addAttribute("selectedPlaceId", selectedPlaceId);
        model.addAttribute("startDate", selectedStartDate);
        model.addAttribute("endDate", selectedEndDate);
        model.addAttribute("timezone", timezone);
        model.addAttribute("isRange", true);
        model.addAttribute("timeDisplayMode", userSettingsJdbcService.getOrCreateDefaultSettings(user.getId()).getTimeDisplayMode());
        return "fragments/timeline :: timeline-content";
    }

    private List<UserTimelineData> handleSharedUserData(User user, LocalDate selectedDate, ZoneId userTimezone, Instant startOfDay, Instant endOfDay) {
        return this.userSharingJdbcService.findBySharedWithUser(user.getId()).stream()
                .map(u -> {
                    Optional<User> sharedWithUserOpt = this.userJdbcService.findById(u.getSharingUserId());
                    return sharedWithUserOpt.map(sharedWithUser -> {
                        List<TimelineEntry> userTimelineEntries = this.timelineService.buildTimelineEntries(sharedWithUser, userTimezone, selectedDate, startOfDay, endOfDay);
                        String currentUserRawLocationPointsUrl = String.format("/api/v1/raw-location-points/%d?date=%s&timezone=%s", sharedWithUser.getId(), selectedDate, userTimezone.getId());
                        String currentUserProcessedVisitsUrl = String.format("/api/v1/visits/%d?date=%s&timezone=%s", sharedWithUser.getId(), selectedDate, userTimezone.getId());
                        String currentUserAvatarUrl = this.avatarService.getInfo(sharedWithUser.getId()).map(avatarInfo -> String.format("/avatars/%d?ts=%s", sharedWithUser.getId(), avatarInfo.updatedAt())).orElse(String.format("/avatars/%d", sharedWithUser.getId()));
                        String currentUserInitials = this.avatarService.generateInitials(sharedWithUser.getDisplayName());

                        return new UserTimelineData(sharedWithUser.getId() + "",
                                sharedWithUser.getDisplayName(),
                                currentUserInitials,
                                currentUserAvatarUrl,
                                u.getColor(),
                                userTimelineEntries,
                                currentUserRawLocationPointsUrl,
                                currentUserProcessedVisitsUrl);
                    });
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted(Comparator.comparing(UserTimelineData::displayName))
                .toList();
    }

    private List<UserTimelineData> handleSharedUserDataRange(User user, LocalDate startDate, LocalDate endDate, ZoneId userTimezone) {
        return this.userSharingJdbcService.findBySharedWithUser(user.getId()).stream()
                .map(u -> {
                    Optional<User> sharedWithUserOpt = this.userJdbcService.findById(u.getSharingUserId());
                    return sharedWithUserOpt.map(sharedWithUser -> {
                        Instant startOfRange = startDate.atStartOfDay(userTimezone).toInstant();
                        Instant endOfRange = endDate.plusDays(1).atStartOfDay(userTimezone).toInstant().minusMillis(1);
                        
                        List<TimelineEntry> userTimelineEntries = this.timelineService.buildTimelineEntries(sharedWithUser, userTimezone, startDate, startOfRange, endOfRange);
                        String currentUserRawLocationPointsUrl = String.format("/api/v1/raw-location-points/%d?startDate=%s&endDate=%s&timezone=%s", sharedWithUser.getId(), startDate, endDate, userTimezone.getId());
                        String currentUserProcessedVisitsUrl = String.format("/api/v1/visits/%d?startDate=%s&endDate=%s&timezone=%s", sharedWithUser.getId(), startDate, endDate, userTimezone.getId());
                        String currentUserAvatarUrl = this.avatarService.getInfo(sharedWithUser.getId()).map(avatarInfo -> String.format("/avatars/%d?ts=%s", sharedWithUser.getId(), avatarInfo.updatedAt())).orElse(String.format("/avatars/%d", sharedWithUser.getId()));
                        String currentUserInitials = this.avatarService.generateInitials(sharedWithUser.getDisplayName());

                        return new UserTimelineData(sharedWithUser.getId() + "",
                                sharedWithUser.getDisplayName(),
                                currentUserInitials,
                                currentUserAvatarUrl,
                                u.getColor(),
                                userTimelineEntries,
                                currentUserRawLocationPointsUrl,
                                currentUserProcessedVisitsUrl);
                    });
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted(Comparator.comparing(UserTimelineData::displayName))
                .toList();
    }
}
