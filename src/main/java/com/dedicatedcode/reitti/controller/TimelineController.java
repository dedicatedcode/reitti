package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.dto.TimelineData;
import com.dedicatedcode.reitti.dto.TimelineEntry;
import com.dedicatedcode.reitti.dto.UserTimelineData;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.SignificantPlaceJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.service.AvatarService;
import com.dedicatedcode.reitti.service.TimelineService;
import com.dedicatedcode.reitti.service.integration.ReittiIntegrationService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Controller
@RequestMapping("/timeline")
public class TimelineController {

    private final SignificantPlaceJdbcService placeService;
    private final UserJdbcService userJdbcService;

    private final AvatarService avatarService;
    private final ReittiIntegrationService reittiIntegrationService;
    private final TimelineService timelineService;
    private final UserSettingsJdbcService userSettingsJdbcService;

    @Autowired
    public TimelineController(SignificantPlaceJdbcService placeService,
                              UserJdbcService userJdbcService,
                              AvatarService avatarService,
                              ReittiIntegrationService reittiIntegrationService,
                              TimelineService timelineService,
                              UserSettingsJdbcService userSettingsJdbcService) {
        this.placeService = placeService;
        this.userJdbcService = userJdbcService;
        this.avatarService = avatarService;
        this.reittiIntegrationService = reittiIntegrationService;
        this.timelineService = timelineService;
        this.userSettingsJdbcService = userSettingsJdbcService;
    }

    @GetMapping("/content")
    public String getTimelineContent(@RequestParam String date,
                                     @RequestParam(required = false, defaultValue = "UTC") String timezone,
                                     Authentication principal, Model model) {
        return getTimelineContent(date, timezone, principal, model, null);
    }


    @GetMapping("/places/edit-form/{id}")
    public String getPlaceEditForm(@PathVariable Long id,
                                   @RequestParam(required = false) String date,
                                   @RequestParam(required = false) String timezone,
                                   Model model) {
        SignificantPlace place = placeService.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("place", place);
        model.addAttribute("placeTypes", SignificantPlace.PlaceType.values());
        model.addAttribute("date", date);
        model.addAttribute("timezone", timezone);
        return "fragments/place-edit :: edit-form";
    }

    @PutMapping("/places/{id}")
    public String updatePlace(@PathVariable Long id,
                              @RequestParam String name,
                              @RequestParam(required = false) String type,
                              @RequestParam(required = false) String date,
                              @RequestParam(required = false, defaultValue = "UTC") String timezone,
                              Authentication principal,
                              Model model) {
        SignificantPlace place = placeService.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        SignificantPlace updatedPlace = place.withName(name);

        if (type != null && !type.isEmpty()) {
            try {
                SignificantPlace.PlaceType placeType = SignificantPlace.PlaceType.valueOf(type);
                updatedPlace = updatedPlace.withType(placeType);
            } catch (IllegalArgumentException e) {
                // Invalid place type, ignore and just update name
            }
        }

        placeService.update(updatedPlace);

        // If we have timeline context, reload the entire timeline with the edited place selected
        if (date != null) {
            return getTimelineContent(date, timezone, principal, model, id);
        }

        // Otherwise just return the updated place view
        model.addAttribute("place", updatedPlace);
        return "fragments/place-edit :: view-mode";
    }

    @GetMapping("/places/view/{id}")
    public String getPlaceView(@PathVariable Long id, Model model) {
        SignificantPlace place = placeService.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("place", place);
        return "fragments/place-edit :: view-mode";
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

        String currentUserAvatarUrl = this.avatarService.getInfo(user.getId()).map(avatarInfo -> String.format("/avatars/%d?ts=%s", user.getId(), avatarInfo.updatedAt())).orElse(String.format("/avatars/%d", user.getId()));
        String currentUserRawLocationPointsUrl = String.format("/api/v1/raw-location-points/%d?date=%s&timezone=%s", user.getId(), date, timezone);
        String currentUserInitials = this.avatarService.generateInitials(user.getDisplayName());
        allUsersData.add(new UserTimelineData(user.getId() + "", user.getUsername(), currentUserInitials, currentUserAvatarUrl, null, currentUserEntries, currentUserRawLocationPointsUrl));

        //Only add this if were are a regular User
        if (authorities.contains("ROLE_USER") || authorities.contains("ROLE_ADMIN")) {
            allUsersData.addAll(this.reittiIntegrationService.getTimelineData(user, selectedDate, userTimezone));
        }
        // Create timeline data record
        TimelineData timelineData = new TimelineData(allUsersData.stream().filter(Objects::nonNull).toList());

        model.addAttribute("timelineData", timelineData);
        model.addAttribute("selectedPlaceId", selectedPlaceId);
        model.addAttribute("data", selectedDate);
        model.addAttribute("timezone", timezone);
        model.addAttribute("timeDisplayMode", userSettingsJdbcService.getOrCreateDefaultSettings(user.getId()).getTimeDisplayMode());
        return "fragments/timeline :: timeline-content";
    }
}
