package com.dedicatedcode.reitti.controller.api.v2;

import com.dedicatedcode.reitti.dto.MapMetadata;
import com.dedicatedcode.reitti.model.security.TokenUser;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.repository.UserSharingJdbcService;
import com.dedicatedcode.reitti.service.StreamingRawLocationPointJdbcService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v2/locations")
public class LocationApiController {

    private final UserJdbcService userJdbcService;
    private final UserSharingJdbcService userSharingJdbcService;
    private final RawLocationPointJdbcService jdbcService;
    private final StreamingRawLocationPointJdbcService streamingRawLocationPointJdbcService;

    public LocationApiController(UserJdbcService userJdbcService,
                                 UserSharingJdbcService userSharingJdbcService,
                                 RawLocationPointJdbcService jdbcService,
                                 StreamingRawLocationPointJdbcService streamingRawLocationPointJdbcService) {
        this.userJdbcService = userJdbcService;
        this.userSharingJdbcService = userSharingJdbcService;
        this.jdbcService = jdbcService;
        this.streamingRawLocationPointJdbcService = streamingRawLocationPointJdbcService;
    }

    @GetMapping("/metadata/{userId}")
    public MapMetadata get(@AuthenticationPrincipal User user,
                           @PathVariable Long userId,
                           @RequestParam String start,
                           @RequestParam String end,
                           @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone) throws IllegalAccessException {
        User userToFetchDataFrom = loadUserToFetchDataFrom(user, userId);
        return this.jdbcService.getMetadata(userToFetchDataFrom, parseInstant(start, timezone), parseInstant(end, timezone).plus(1, ChronoUnit.DAYS));
    }

    private User loadUserToFetchDataFrom(User user, Long userId) throws IllegalAccessException {
        if (user.getId().equals(userId)) {
            return user;
        }
        if (user instanceof TokenUser) {
            if (!Objects.equals(user.getId(), userId)) {
                throw new IllegalAccessException("User not allowed to fetch raw location points for other users");
            }
        }
        if (this.userSharingJdbcService.findBySharedWithUser(user.getId()).stream().noneMatch(userSharing -> userSharing.getSharingUserId().equals(userId))) {
            throw new IllegalAccessException("User not allowed to fetch raw location points for other user with id " + userId);
        }

        return userJdbcService.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @GetMapping(value = "/stream/{userId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<ResponseBodyEmitter> stream(
            @AuthenticationPrincipal User user,
            @PathVariable Long userId,
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone) throws IllegalAccessException {
        User userToFetchDataFrom = loadUserToFetchDataFrom(user, userId);
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(0L);

        CompletableFuture.runAsync(() -> {
            try {
                streamingRawLocationPointJdbcService.streamPoints(userToFetchDataFrom.getId(), parseInstant(start, timezone), parseInstant(end, timezone).plus(1, ChronoUnit.DAYS), emitter);
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .header(HttpHeaders.CONTENT_ENCODING, "identity")
                .body(emitter);
    }

    private Instant parseInstant(String input, ZoneId timezone) {
        LocalDateTime dateTime = null;
        try {
            dateTime = LocalDateTime.parse(input);
        } catch (Exception ignores) {
        }

        if (dateTime == null) {
            try {
                dateTime = LocalDateTime.parse(input + "T00:00:00");
            } catch (Exception e) {
                throw new IllegalArgumentException("Unable to parse date: " + input);
            }
        }
        return dateTime.atZone(timezone).toInstant();
    }
}
