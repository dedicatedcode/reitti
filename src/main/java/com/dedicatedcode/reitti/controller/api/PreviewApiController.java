package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.dto.timeline.SingleTimelineEntry;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSettings;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.dedicatedcode.reitti.service.TimeUtil;
import com.dedicatedcode.reitti.service.TimelineService;
import com.dedicatedcode.reitti.service.VisitDetectionPreviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/preview")
public class PreviewApiController {
    private final TimelineService timelineService;
    private final VisitDetectionPreviewService visitDetectionPreviewService;
    private final UserSettingsJdbcService userSettingsJdbcService;

    public PreviewApiController(TimelineService timelineService, VisitDetectionPreviewService visitDetectionPreviewService, UserSettingsJdbcService userSettingsJdbcService) {
        this.timelineService = timelineService;
        this.visitDetectionPreviewService = visitDetectionPreviewService;
        this.userSettingsJdbcService = userSettingsJdbcService;
    }

    @GetMapping("/{previewId}/status")
    public ResponseEntity<Map<String, Object>> getPreviewStatus(@PathVariable String previewId) {
        boolean ready = visitDetectionPreviewService.isPreviewReady(previewId);
        
        return ResponseEntity.ok(Map.of(
            "ready", ready,
            "previewId", previewId
        ));
    }

    @GetMapping("/{previewId}/timeline")
    public List<SingleTimelineEntry> getPreviewTimeline(@AuthenticationPrincipal User user,
                                                        @PathVariable String previewId,
                                                        @RequestParam String date,
                                                        @RequestParam(required = false, defaultValue = "UTC") String timezone) {
        LocalDate selectedDate = LocalDate.parse(date);
        ZoneId userTimezone = ZoneId.of(timezone);
        UserSettings userSettings = userSettingsJdbcService.getOrCreateDefaultSettings(user.getId());

        Instant startOfDay = TimeUtil.startOfDay(selectedDate, userTimezone, userSettings.getDayStartTime());
        Instant endOfDay = TimeUtil.startOfDay(selectedDate.plusDays(1), userTimezone, userSettings.getDayStartTime());

        return this.timelineService.buildTimelineEntries(user, previewId, userTimezone, selectedDate, startOfDay, endOfDay, false);
    }
}
