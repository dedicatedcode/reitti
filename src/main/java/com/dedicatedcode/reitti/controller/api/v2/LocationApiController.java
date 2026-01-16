package com.dedicatedcode.reitti.controller.api.v2;

import com.dedicatedcode.reitti.dto.MapMetadata;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.service.StreamingRawLocationPointJdbcService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v2/locations")
public class LocationApiController {

    private final RawLocationPointJdbcService jdbcService;
    private final StreamingRawLocationPointJdbcService streamingRawLocationPointJdbcService;

    public LocationApiController(RawLocationPointJdbcService jdbcService,
                                 StreamingRawLocationPointJdbcService streamingRawLocationPointJdbcService) {
        this.jdbcService = jdbcService;
        this.streamingRawLocationPointJdbcService = streamingRawLocationPointJdbcService;
    }

    @GetMapping("/metadata/{userId}")
    public MapMetadata get(@AuthenticationPrincipal User user,
                           @PathVariable Long userId,
                           @RequestParam String start,
                           @RequestParam String end,
                           @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone) {
        return this.jdbcService.getMetadata(userId, parseInstant(start, timezone, false), parseInstant(end, timezone, true));
    }

    @GetMapping(value = "/stream/{userId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<ResponseBodyEmitter> stream(
            @AuthenticationPrincipal User user,
            @PathVariable Long userId,
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone
    ) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(0L);

        CompletableFuture.runAsync(() -> {
            try {
                streamingRawLocationPointJdbcService.streamPoints(userId, parseInstant(start, timezone, false), parseInstant(end, timezone, true), emitter);
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return ResponseEntity.ok(emitter);
    }

    private Instant parseInstant(String input, ZoneId timezone, boolean end) {
        LocalDateTime dateTime = null;
        try {
            dateTime = LocalDateTime.parse(input);
        } catch (Exception ignores) {
        }

        if (dateTime == null) {
            try {
                dateTime = LocalDateTime.parse(input + "T" + (end ? "23:59:59" : "00:00:00"));
                if (end) {
                    dateTime = dateTime.plus(1, ChronoUnit.MILLIS);
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("Unable to parse date: " + input);
            }
        }
        return dateTime.atZone(timezone).toInstant();
    }
}
