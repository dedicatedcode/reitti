package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.dto.timeline.SingleTimelineEntry;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSettings;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.service.TimeUtil;
import com.dedicatedcode.reitti.service.TimelineService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/v1/timeline")
public class TimelineApiController {

    private final TimelineService timelineService;
    private final UserSettingsJdbcService userSettingsJdbcService;

    public TimelineApiController(TimelineService timelineService, UserSettingsJdbcService userSettingsJdbcService) {
        this.timelineService = timelineService;
        this.userSettingsJdbcService = userSettingsJdbcService;
    }

    @GetMapping
    public List<SingleTimelineEntry> getTimeline(@AuthenticationPrincipal User user,
                                                 @RequestParam String date,
                                                 @RequestParam(required = false, defaultValue = "UTC") String timezone) {

        LocalDate selectedDate = LocalDate.parse(date);
        ZoneId userTimezone = ZoneId.of(timezone);
        UserSettings userSettings = userSettingsJdbcService.getOrCreateDefaultSettings(user.getId());

        Instant startOfDay = TimeUtil.startOfDay(selectedDate, userTimezone, userSettings.getDayStartTime());
        Instant endOfDay = TimeUtil.endOfDay(selectedDate, userTimezone, userSettings.getDayStartTime());

        return this.timelineService.buildTimelineEntries(user, userTimezone, selectedDate, startOfDay, endOfDay, true);
    }

}