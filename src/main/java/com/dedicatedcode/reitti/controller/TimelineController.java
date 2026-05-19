package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.dto.TimelineData;
import com.dedicatedcode.reitti.dto.TimelineEntry;
import com.dedicatedcode.reitti.dto.UserTimelineData;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.metadata.MemoryMetadata;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    private final UserJdbcService userJdbcService;

    private final AvatarService avatarService;
    private final ReittiIntegrationService reittiIntegrationService;
    private final UserSharingJdbcService userSharingJdbcService;
    private final TimelineService timelineService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final TransportModeService transportModeService;
    private final TripJdbcService tripJdbcService;

    @Autowired
    public TimelineController(UserJdbcService userJdbcService,
                              AvatarService avatarService,
                              ReittiIntegrationService reittiIntegrationService,
                              UserSharingJdbcService userSharingJdbcService,
                              TimelineService timelineService,
                              UserSettingsJdbcService userSettingsJdbcService,
                              TransportModeService transportModeService,
                              TripJdbcService tripJdbcService) {
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
                                          @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
                                          Authentication principal, Model model) {

        List<String> authorities = principal.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();

        LocalDate now = LocalDate.now(timezone);

        // Check if any date in the range is not today
        if (!startDate.isEqual(now) || !endDate.isEqual(now)) {
            if (!authorities.contains("ROLE_USER") && !authorities.contains("ROLE_ADMIN") && !authorities.contains("ROLE_MAGIC_LINK_FULL_ACCESS")) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
        }
        List<UserTimelineData> allUsersData = new ArrayList<>();

        User user = userJdbcService.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        UserTimelineData userData = createUserTimeLineData(user, authorities, startDate, endDate, timezone, true);
        allUsersData.add(userData);

        if (authorities.contains("ROLE_USER") || authorities.contains("ROLE_ADMIN")) {
            allUsersData.addAll(this.reittiIntegrationService.getTimelineDataRange(user, startDate, endDate, timezone));
            allUsersData.addAll(handleSharedUserDataRange(user, startDate, endDate, timezone, true));
        }

        TimelineData timelineData = new TimelineData(allUsersData.stream().filter(Objects::nonNull).toList());

        model.addAttribute("timelineData", timelineData);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("timezone", timezone);
        model.addAttribute("isRange", true);
        model.addAttribute("timeDisplayMode", userSettingsJdbcService.getOrCreateDefaultSettings(user.getId()).getTimeDisplayMode());
        return "fragments/timeline :: timeline-content";
    }

    private UserTimelineData createUserTimeLineData(User user, List<String> authorities, LocalDate startDate, LocalDate endDate, ZoneId timezone, boolean loadTimeline) {
        Instant startOfRange = startDate.atStartOfDay(timezone).toInstant();
        Instant endOfRange = endDate.plusDays(1).atStartOfDay(timezone).toInstant().minusMillis(1);

        List<TimelineEntry> currentUserEntries;
        if (loadTimeline && (authorities.contains("ROLE_USER") || authorities.contains("ROLE_ADMIN") || authorities.contains("ROLE_MAGIC_LINK_FULL_ACCESS"))) {
            currentUserEntries = this.timelineService.buildTimelineEntries(user, timezone, startDate, startOfRange, endOfRange);
        } else {
            currentUserEntries = Collections.emptyList();
        }

        UserSettings userSettings = userSettingsJdbcService.getOrCreateDefaultSettings(user.getId());
        boolean loadVisits = authorities.contains("ROLE_USER") || authorities.contains("ROLE_ADMIN") || authorities.contains("ROLE_MAGIC_LINK_FULL_ACCESS");
        boolean loadPaths = authorities.contains("ROLE_USER") || authorities.contains("ROLE_ADMIN") || authorities.contains("ROLE_MAGIC_LINK_FULL_ACCESS") || authorities.contains("ROLE_MAGIC_LINK_ONLY_LIVE_WITH_PHOTOS") || authorities.contains("ROLE_MAGIC_LINK_ONLY_LIVE");
        String currentUserProcessedVisitsUrl = loadVisits ? String.format("/api/v1/visits/%d?startDate=%s&endDate=%s&timezone=%s", user.getId(), startDate, endDate, timezone.getId()) : null;
        String mapMetaDataUrl = String.format("/api/v2/locations/metadata/%d?start=%s&end=%s&timezone=%s", user.getId(), startDate, endDate, timezone.getId());
        String mapStreamDataUrl = loadPaths ? String.format("/api/v2/locations/stream/%d?start=%s&end=%s&timezone=%s", user.getId(), startDate, endDate, timezone.getId()) : null;
        String currentUserAvatarUrl = this.avatarService.getInfo(user.getId()).map(avatarInfo -> String.format("/avatars/%d?ts=%s", user.getId(), avatarInfo.updatedAt())).orElse(String.format("/avatars/%d", user.getId()));
        String currentUserInitials = this.avatarService.generateInitials(user.getDisplayName());
        return new UserTimelineData(user.getId() + "",
                                    user.getDisplayName(),
                                    currentUserInitials,
                                    currentUserAvatarUrl,
                                    userSettings.getColor(),
                                    currentUserEntries,
                                    null,
                                    currentUserProcessedVisitsUrl,
                                    mapMetaDataUrl,
                                    mapStreamDataUrl);
    }

    @GetMapping("/user-selection")
    public String loadUserSelection(@RequestParam LocalDate startDate,
                                    @RequestParam LocalDate endDate,
                                    @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
                                    Authentication principal,
                                    Model model) {
        List<String> authorities = principal.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        List<UserTimelineData> allUsersData = new ArrayList<>();
        User user = userJdbcService.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        UserTimelineData userData = createUserTimeLineData(user, authorities, startDate, endDate, timezone, false);
        allUsersData.add(userData);

        if (authorities.contains("ROLE_USER") || authorities.contains("ROLE_ADMIN")) {
            allUsersData.addAll(this.reittiIntegrationService.getUserData(user, startDate, endDate, timezone));
            allUsersData.addAll(handleSharedUserDataRange(user, startDate, endDate, timezone, false));
        }

        TimelineData timelineData = new TimelineData(allUsersData.stream().filter(Objects::nonNull).toList());
        model.addAttribute("timelineData", timelineData);
        return "fragments/user-selection :: user-selection";
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

    private List<UserTimelineData> handleSharedUserDataRange(User user, LocalDate startDate, LocalDate endDate, ZoneId userTimezone, boolean loadTimeline) {
        return this.userSharingJdbcService.findBySharedWithUser(user.getId()).stream()
                .map(u -> {
                    Optional<User> sharedWithUserOpt = this.userJdbcService.findById(u.getSharingUserId());
                    return sharedWithUserOpt.map(sharedWithUser -> {
                        Instant startOfRange = startDate.atStartOfDay(userTimezone).toInstant();
                        Instant endOfRange = endDate.plusDays(1).atStartOfDay(userTimezone).toInstant().minusMillis(1);
                        
                        List<TimelineEntry> userTimelineEntries = loadTimeline ? this.timelineService.buildTimelineEntries(sharedWithUser, userTimezone, startDate, startOfRange, endOfRange) : Collections.emptyList();
                        String currentUserRawLocationPointsUrl = String.format("/api/v1/raw-location-points/%d?startDate=%s&endDate=%s&timezone=%s", sharedWithUser.getId(), startDate, endDate, userTimezone.getId());
                        String currentUserProcessedVisitsUrl = String.format("/api/v1/visits/%d?startDate=%s&endDate=%s&timezone=%s", sharedWithUser.getId(), startDate, endDate, userTimezone.getId());
                        String mapMetaDataUrl = String.format("/api/v2/locations/metadata/%d?start=%s&end=%s&timezone=%s", sharedWithUser.getId(), startDate, endDate, userTimezone.getId());
                        String mapStreamDataUrl = String.format("/api/v2/locations/stream/%d?start=%s&end=%s&timezone=%s", sharedWithUser.getId(), startDate, endDate, userTimezone.getId());
                        String currentUserAvatarUrl = this.avatarService.getInfo(sharedWithUser.getId()).map(avatarInfo -> String.format("/avatars/%d?ts=%s", sharedWithUser.getId(), avatarInfo.updatedAt())).orElse(String.format("/avatars/%d", sharedWithUser.getId()));
                        String currentUserInitials = this.avatarService.generateInitials(sharedWithUser.getDisplayName());

                        return new UserTimelineData(sharedWithUser.getId() + "",
                                                    sharedWithUser.getDisplayName(),
                                                    currentUserInitials,
                                                    currentUserAvatarUrl,
                                                    u.getColor(),
                                                    userTimelineEntries,
                                                    currentUserRawLocationPointsUrl,
                                                    currentUserProcessedVisitsUrl,
                                                    mapMetaDataUrl,
                                                    mapStreamDataUrl);
                    });
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted(Comparator.comparing(UserTimelineData::displayName))
                .toList();
    }
}
