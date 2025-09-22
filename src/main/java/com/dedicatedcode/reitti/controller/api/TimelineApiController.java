package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.dto.TimelineEntry;
import com.dedicatedcode.reitti.model.security.User;
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

    public TimelineApiController(TimelineService timelineService) {
        this.timelineService = timelineService;
    }

    @GetMapping
    public List<TimelineEntry> getTimeline(@AuthenticationPrincipal User user,
                                           @RequestParam String date,
                                           @RequestParam(required = false, defaultValue = "UTC") String timezone) {

        LocalDate selectedDate = LocalDate.parse(date);
        ZoneId userTimezone = ZoneId.of(timezone);

        Instant startOfDay = selectedDate.atStartOfDay(userTimezone).toInstant();
        Instant endOfDay = selectedDate.plusDays(1).atStartOfDay(userTimezone).toInstant().minusMillis(1);

        return this.timelineService.buildTimelineEntries(user, userTimezone, selectedDate, startOfDay, endOfDay);
    }

}
