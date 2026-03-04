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
import java.time.ZonedDateTime;
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
        Instant startInstant = parseInstant(start, timezone, false);
        Instant endInstant = parseInstant(end, timezone, true).plus(1, ChronoUnit.SECONDS);
        return this.jdbcService.getMetadata(userToFetchDataFrom, startInstant, endInstant);
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
                streamingRawLocationPointJdbcService.streamPoints(userToFetchDataFrom.getId(), parseInstant(start, timezone, false), parseInstant(end, timezone, true).plus(1, ChronoUnit.SECONDS), emitter);
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
    private Instant parseInstant(String input, ZoneId timezone, boolean end) {
        LocalDateTime dateTime = null;
        try {
            dateTime = LocalDateTime.parse(input);
        } catch (Exception ignored) {}

        if (dateTime == null) {
            try {
                dateTime = LocalDateTime.parse(input + (end ? "T23:59:59" : "T00:00:00"));
                return dateTime.atZone(timezone).toInstant();
            } catch (Exception ignored) {}
        }
        if (dateTime == null) {
            try {
                dateTime = ZonedDateTime.parse(input).toLocalDateTime();
                return dateTime.atZone(timezone).toInstant();
            } catch (Exception ignored) {}
        }
        throw new IllegalArgumentException("Invalid date format");
    }
}
