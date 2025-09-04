package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.dto.ReittiRemoteInfo;
import com.dedicatedcode.reitti.dto.SubscriptionRequest;
import com.dedicatedcode.reitti.dto.SubscriptionResponse;
import com.dedicatedcode.reitti.dto.TimelineEntry;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.service.TimelineService;
import com.dedicatedcode.reitti.service.UserNotificationService;
import com.dedicatedcode.reitti.service.VersionService;
import com.dedicatedcode.reitti.service.integration.ReittiSubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reitti-integration")
public class ReittiIntegrationApiController {
    private final VersionService versionService;
    private final TimelineService timelineService;
    private final ReittiSubscriptionService subscriptionService;
    private final UserNotificationService userNotificationService;

    public ReittiIntegrationApiController(VersionService versionService,
                                          TimelineService timelineService,
                                          ReittiSubscriptionService subscriptionService,
                                          UserNotificationService userNotificationService) {
        this.versionService = versionService;
        this.timelineService = timelineService;
        this.subscriptionService = subscriptionService;
        this.userNotificationService = userNotificationService;
    }

    @GetMapping("/info")
    public ResponseEntity<ReittiRemoteInfo> getInfo(@AuthenticationPrincipal User user) {
        ReittiRemoteInfo.RemoteUserInfo userInfo = new ReittiRemoteInfo.RemoteUserInfo(user.getId(), user.getUsername(), user.getDisplayName(), user.getVersion());
        ReittiRemoteInfo.RemoteServerInfo serverInfo = new ReittiRemoteInfo.RemoteServerInfo("Reitti", this.versionService.getVersion(), LocalDateTime.now());
        return ResponseEntity.ok(new ReittiRemoteInfo(userInfo, serverInfo));
    }

    @GetMapping("/timeline")
    public List<TimelineEntry> getTimeline(@AuthenticationPrincipal User user,
                                           @RequestParam String date,
                                           @RequestParam(required = false, defaultValue = "UTC") String timezone) {

        LocalDate selectedDate = LocalDate.parse(date);
        ZoneId userTimezone = ZoneId.of(timezone);

        // Convert LocalDate to start and end Instant for the selected date in user's timezone
        Instant startOfDay = selectedDate.atStartOfDay(userTimezone).toInstant();
        Instant endOfDay = selectedDate.plusDays(1).atStartOfDay(userTimezone).toInstant().minusMillis(1);

        return this.timelineService.buildTimelineEntries(user, userTimezone, selectedDate, startOfDay, endOfDay);
    }

    @PostMapping("/subscribe")
    public ResponseEntity<SubscriptionResponse> subscribe(@AuthenticationPrincipal User user,
                                                         @Valid @RequestBody SubscriptionRequest request) {
        SubscriptionResponse response = subscriptionService.createSubscription(user, request.getCallbackUrl());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/notify/{subscriptionId}")
    public ResponseEntity<Void> notify(@PathVariable String subscriptionId,
                                      @RequestBody Object notificationData) {
        try {
            // Verify subscription exists
            if (subscriptionService.getSubscription(subscriptionId) == null) {
                return ResponseEntity.notFound().build();
            }


            this.userNotificationService.
            // Process the notification - this endpoint receives notifications from external systems
            // The actual notification processing logic would go here
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
