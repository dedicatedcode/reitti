package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.dto.TimelineEntry;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.TimelineService;
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

    public PreviewApiController(TimelineService timelineService) {
        this.timelineService = timelineService;
    }

    @GetMapping("/{previewId}/status")
    public ResponseEntity<Map<String, Object>> getPreviewStatus(@PathVariable String previewId) {
        boolean ready = true;
        
        return ResponseEntity.ok(Map.of(
            "ready", ready,
            "previewId", previewId
        ));
    }

    @GetMapping("/{previewId}/timeline")
    public List<TimelineEntry> getPreviewTimeline(@AuthenticationPrincipal User user,
                                                  @PathVariable String previewId,
                                                  @RequestParam String date,
                                                  @RequestParam(required = false, defaultValue = "UTC") String timezone) {
        LocalDate selectedDate = LocalDate.parse(date);
        ZoneId userTimezone = ZoneId.of(timezone);

        Instant startOfDay = selectedDate.atStartOfDay(userTimezone).toInstant();
        Instant endOfDay = selectedDate.plusDays(1).atStartOfDay(userTimezone).toInstant().minusMillis(1);

        return this.timelineService.buildTimelineEntries(user, previewId, userTimezone, selectedDate, startOfDay, endOfDay);
    }
}
