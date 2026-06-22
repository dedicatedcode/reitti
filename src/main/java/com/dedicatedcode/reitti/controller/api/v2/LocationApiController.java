package com.dedicatedcode.reitti.controller.api.v2;

import com.dedicatedcode.reitti.dto.MapMetadata;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.TokenUser;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.*;
import com.dedicatedcode.reitti.service.GeoJsonExportService;
import com.dedicatedcode.reitti.service.StreamingRawLocationPointJdbcService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
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
    private final DeviceJdbcService deviceJdbcService;
    private final UserSharingJdbcService userSharingJdbcService;
    private final RawLocationPointJdbcService jdbcService;
    private final SourceLocationPointJdbcService sourceLocationPointJdbcService;
    private final GeoJsonExportService geoJsonExportService;
    private final StreamingRawLocationPointJdbcService streamingRawLocationPointJdbcService;

    public LocationApiController(UserJdbcService userJdbcService,
                                 DeviceJdbcService deviceJdbcService,
                                 UserSharingJdbcService userSharingJdbcService,
                                 RawLocationPointJdbcService jdbcService,
                                 SourceLocationPointJdbcService sourceLocationPointJdbcService,
                                 GeoJsonExportService geoJsonExportService,
                                 StreamingRawLocationPointJdbcService streamingRawLocationPointJdbcService) {
        this.userJdbcService = userJdbcService;
        this.deviceJdbcService = deviceJdbcService;
        this.userSharingJdbcService = userSharingJdbcService;
        this.jdbcService = jdbcService;
        this.sourceLocationPointJdbcService = sourceLocationPointJdbcService;
        this.geoJsonExportService = geoJsonExportService;
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

    @GetMapping("/metadata/{userId}/device/{deviceId}")
    public MapMetadata getForDevice(@AuthenticationPrincipal User user,
                           @PathVariable Long userId,
                           @PathVariable Long deviceId,
                           @RequestParam String start,
                           @RequestParam String end,
                           @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone) throws IllegalAccessException {
        User userToFetchDataFrom = loadUserToFetchDataFrom(user, userId);
        Device device = deviceJdbcService.find(userToFetchDataFrom, deviceId).orElseThrow(() -> new IllegalArgumentException("Device not found"));

        Instant startInstant = parseInstant(start, timezone, false);
        Instant endInstant = parseInstant(end, timezone, true).plus(1, ChronoUnit.SECONDS);
        return this.sourceLocationPointJdbcService.getMetadata(userToFetchDataFrom, device, startInstant, endInstant);
    }

    @GetMapping(value = "/stream/{userId}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
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
                streamingRawLocationPointJdbcService.streamPoints(userToFetchDataFrom, parseInstant(start, timezone, false), parseInstant(end, timezone, true).plus(1, ChronoUnit.SECONDS), emitter);
            } catch (Exception e) {
                if (e.getCause() instanceof java.io.IOException) {
                    try { emitter.complete(); } catch (Exception ignored) {}
                } else {
                    try { emitter.completeWithError(e); } catch (Exception ignored) {}
                }
            }
        });

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .header(HttpHeaders.CONTENT_ENCODING, "identity")
                .body(emitter);
    }

    @GetMapping(value = "/stream/{userId}/device/{deviceId}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<ResponseBodyEmitter> streamForDevice(
            @AuthenticationPrincipal User user,
            @PathVariable Long userId,
            @PathVariable Long deviceId,
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone) throws IllegalAccessException {
        User userToFetchDataFrom = loadUserToFetchDataFrom(user, userId);
        Device device = deviceJdbcService.find(userToFetchDataFrom, deviceId).orElseThrow(() -> new IllegalArgumentException("Device not found"));

        ResponseBodyEmitter emitter = new ResponseBodyEmitter(0L);

        CompletableFuture.runAsync(() -> {
            try {
                streamingRawLocationPointJdbcService.streamPoints(userToFetchDataFrom, device, parseInstant(start, timezone, false), parseInstant(end, timezone, true).plus(1, ChronoUnit.SECONDS), emitter);
            } catch (Exception e) {
                if (e.getCause() instanceof java.io.IOException) {
                    try { emitter.complete(); } catch (Exception ignored) {}
                } else {
                    try { emitter.completeWithError(e); } catch (Exception ignored) {}
                }
            }
        });

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .header(HttpHeaders.CONTENT_ENCODING, "identity")
                .body(emitter);
    }

    @GetMapping(value = "/geojson/source", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamingResponseBody> loadAsGeoJson(@AuthenticationPrincipal User user,
                                                               @RequestParam(name = "device", required = false) Long deviceId,
                                                               @RequestParam String start,
                                                               @RequestParam String end,
                                                               @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone) {
        try {
            StreamingResponseBody stream = outputStream -> {
                try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                    geoJsonExportService.generateGeoJsonContentStreaming(
                            user,
                            parseInstant(start, timezone, false),
                            parseInstant(end, timezone, true),
                            deviceId,
                            writer);
                } catch (Exception e) {
                    throw new RuntimeException("Error generating GeoJSON file", e);
                }
            };

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(stream);

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(outputStream -> {
                        try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                            writer.write("Error generating GeoJSON Stream: " + e.getMessage());
                        } catch (IOException ioException) {
                            throw new RuntimeException(ioException);
                        }
                    });
        }
    }

    @GetMapping(value = "/geojson", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamingResponseBody> loadTimelineAsGeoJson(@AuthenticationPrincipal User user,
                                                               @RequestParam String start,
                                                               @RequestParam String end,
                                                               @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone) {
        try {
            StreamingResponseBody stream = outputStream -> {
                try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                    geoJsonExportService.generateGeoJsonContentStreaming(
                            user,
                            parseInstant(start, timezone, false),
                            parseInstant(end, timezone, true),
                            writer);
                } catch (Exception e) {
                    throw new RuntimeException("Error generating GeoJSON file", e);
                }
            };

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(stream);

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(outputStream -> {
                        try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                            writer.write("Error generating GeoJSON Stream: " + e.getMessage());
                        } catch (IOException ioException) {
                            throw new RuntimeException(ioException);
                        }
                    });
        }
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
        try {
            return LocalDateTime.parse(input).atZone(timezone).toInstant();
        } catch (Exception ignored) {
        }

        try {
            return LocalDateTime.parse(input + (end ? "T23:59:59" : "T00:00:00")).atZone(timezone).toInstant();
        } catch (Exception ignored) {
        }
        try {
            return ZonedDateTime.parse(input).toInstant();
        } catch (Exception ignored) {
        }
        throw new IllegalArgumentException("Invalid date format");
    }
}
