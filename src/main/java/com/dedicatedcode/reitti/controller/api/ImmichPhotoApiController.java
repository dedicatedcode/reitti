package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.dto.PhotoResponse;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSharing;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.repository.UserSharingJdbcService;
import com.dedicatedcode.reitti.service.TimeUtil;
import com.dedicatedcode.reitti.service.integration.ImmichIntegrationService;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/photos/immich")
public class ImmichPhotoApiController {

    private final ImmichIntegrationService immichIntegrationService;
    private final UserSharingJdbcService userSharingJdbcService;
    private final UserJdbcService userJdbcService;

    public ImmichPhotoApiController(ImmichIntegrationService immichIntegrationService,
                                    UserSharingJdbcService userSharingJdbcService,
                                    UserJdbcService userJdbcService) {
        this.immichIntegrationService = immichIntegrationService;
        this.userSharingJdbcService = userSharingJdbcService;
        this.userJdbcService = userJdbcService;
    }

    @GetMapping("/range")
    public ResponseEntity<List<PhotoResponse>> getPhotosForRange(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(required = false, defaultValue = "UTC") String timezone,
            @RequestParam(required = false) Long userId,
            @AuthenticationPrincipal User user) {

        User photoOwner = resolvePhotoOwner(user, userId);
        if (photoOwner == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        ZoneId timezoneId = ZoneId.of(timezone);
        List<PhotoResponse> photos = immichIntegrationService.searchPhotosForRange(photoOwner, parseInstant(startDate, timezoneId, false), parseInstant(endDate, timezoneId, true));
        if (!photoOwner.getId().equals(user.getId())) {
            photos.forEach(photo -> photo.setShared(true));
        }
        return ResponseEntity.ok(photos);
    }

    @GetMapping("/proxy/{assetId}/thumbnail")
    public ResponseEntity<byte[]> getPhotoThumbnail(
            @PathVariable String assetId,
            @RequestParam(required = false) Long userId,
            @AuthenticationPrincipal User user) {

        User photoOwner = resolvePhotoOwner(user, userId);
        if (photoOwner == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return immichIntegrationService.proxyImageRequest(photoOwner, assetId, "thumbnail");
    }

    @GetMapping("/proxy/{assetId}/original")
    public ResponseEntity<byte[]> getPhotoOriginal(
            @PathVariable String assetId,
            @RequestParam(required = false) Long userId,
            @AuthenticationPrincipal User user) {

        User photoOwner = resolvePhotoOwner(user, userId);
        if (photoOwner == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return immichIntegrationService.proxyImageRequest(photoOwner, assetId, "fullsize");
    }

    @PutMapping("/{assetId}/location")
    public ResponseEntity<Map<String, Object>> updateAssetLocation(
            @PathVariable String assetId,
            @RequestParam double latitude,
            @RequestParam double longitude,
            @AuthenticationPrincipal User user) {

        boolean success = immichIntegrationService.updateAssetLocation(user, assetId, latitude, longitude);
        if (success) {
            return ResponseEntity.ok(Map.of("status", "ok"));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("status", "error", "message", "Failed to update asset location in Immich"));
        }
    }

    private User resolvePhotoOwner(User currentUser, Long userId) {
        if (userId == null || userId.equals(currentUser.getId())) {
            return currentUser;
        }

        Optional<UserSharing> sharing = userSharingJdbcService.findBySharedWithUser(currentUser.getId()).stream()
                .filter(s -> userId.equals(s.getSharingUserId()))
                .filter(UserSharing::isSharePhotos)
                .findFirst();
        if (sharing.isEmpty()) {
            return null;
        }

        return userJdbcService.findById(userId).orElse(null);
    }

    private Instant parseInstant(String input, ZoneId timezone, boolean end) {
        try {
            return LocalDateTime.parse(input).atZone(timezone).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        LocalDate date = LocalDate.parse(input);
        return end ? TimeUtil.endOfDay(date, timezone, LocalTime.MIDNIGHT)
                   : TimeUtil.startOfDay(date, timezone, LocalTime.MIDNIGHT);
    }

}
