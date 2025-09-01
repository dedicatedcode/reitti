package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.dto.TimelineData;
import com.dedicatedcode.reitti.dto.TimelineEntry;
import com.dedicatedcode.reitti.dto.UserTimelineData;
import com.dedicatedcode.reitti.model.SignificantPlace;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.SignificantPlaceJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.AvatarService;
import com.dedicatedcode.reitti.service.TimelineService;
import com.dedicatedcode.reitti.service.integration.ReittiIntegrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Controller
@RequestMapping("/timeline")
public class TimelineController {

    private static final Logger log = LoggerFactory.getLogger(TimelineController.class);
    private final SignificantPlaceJdbcService placeService;
    private final UserJdbcService userJdbcService;

    private final AvatarService avatarService;
    private final ReittiIntegrationService reittiIntegrationService;
    private final TimelineService timelineService;

    @Autowired
    public TimelineController(SignificantPlaceJdbcService placeService,
                              UserJdbcService userJdbcService,
                              AvatarService avatarService,
                              ReittiIntegrationService reittiIntegrationService,
                              TimelineService timelineService) {
        this.placeService = placeService;
        this.userJdbcService = userJdbcService;
        this.avatarService = avatarService;
        this.reittiIntegrationService = reittiIntegrationService;
        this.timelineService = timelineService;
    }

    @GetMapping("/content")
    public String getTimelineContent(@RequestParam String date,
                                     @RequestParam(required = false, defaultValue = "UTC") String timezone,
                                     Principal principal, Model model) {
        LocalDate selectedDate = LocalDate.parse(date);
        ZoneId userTimezone = ZoneId.of(timezone);
        
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

        allUsersData.addAll(this.reittiIntegrationService.getTimelineData(user, selectedDate, userTimezone));
        // Create timeline data record
        TimelineData timelineData = new TimelineData(allUsersData.stream().filter(Objects::nonNull).toList());
        
        model.addAttribute("timelineData", timelineData);
        return "fragments/timeline :: timeline-content";
    }



    @GetMapping("/places/edit-form/{id}")
    public String getPlaceEditForm(@PathVariable Long id, Model model) {
        SignificantPlace place = placeService.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("place", place);
        model.addAttribute("placeTypes", SignificantPlace.PlaceType.values());
        return "fragments/place-edit :: edit-form";
    }

    @PutMapping("/places/{id}")
    public String updatePlace(@PathVariable Long id, 
                              @RequestParam String name, 
                              @RequestParam(required = false) String type, 
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
        
        model.addAttribute("place", placeService.update(updatedPlace));
        return "fragments/place-edit :: view-mode";
    }

    @GetMapping("/places/view/{id}")
    public String getPlaceView(@PathVariable Long id, Model model) {
        SignificantPlace place = placeService.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("place", place);
        return "fragments/place-edit :: view-mode";
    }
}
