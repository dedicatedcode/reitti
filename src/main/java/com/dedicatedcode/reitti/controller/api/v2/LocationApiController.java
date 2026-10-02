package com.dedicatedcode.reitti.controller.api.v2;

import com.dedicatedcode.reitti.controller.api.DataAccessGuard;
import com.dedicatedcode.reitti.controller.api.DataAccessGuard.TimeWindow;
import com.dedicatedcode.reitti.dto.MapMetadata;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.MagicLinkAccessLevel;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.DeviceJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v2/locations")
public class LocationApiController {

    private final DataAccessGuard dataAccessGuard;
    private final DeviceJdbcService deviceJdbcService;
    private final RawLocationPointJdbcService jdbcService;
    private final SourceLocationPointJdbcService sourceLocationPointJdbcService;
    private final GeoJsonExportService geoJsonExportService;
    private final StreamingRawLocationPointJdbcService streamingRawLocationPointJdbcService;

    public LocationApiController(DataAccessGuard dataAccessGuard,
                                 DeviceJdbcService deviceJdbcService,
                                 RawLocationPointJdbcService jdbcService,
                                 SourceLocationPointJdbcService sourceLocationPointJdbcService,
                                 GeoJsonExportService geoJsonExportService,
                                 StreamingRawLocationPointJdbcService streamingRawLocationPointJdbcService) {
        this.dataAccessGuard = dataAccessGuard;
        this.deviceJdbcService = deviceJdbcService;
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
                           @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone) {
        User userToFetchDataFrom = dataAccessGuard.loadUserToFetchDataFrom(user, userId);
        TimeWindow range = readableRange(user, start, end, timezone,
                MagicLinkAccessLevel.FULL_ACCESS, MagicLinkAccessLevel.ONLY_LIVE, MagicLinkAccessLevel.ONLY_LIVE_WITH_PHOTOS,
                MagicLinkAccessLevel.ONLY_LAST_LOCATION, MagicLinkAccessLevel.MEMORY_VIEW_ONLY, MagicLinkAccessLevel.MEMORY_EDIT_ACCESS);
        MapMetadata metadata = this.jdbcService.getMetadata(userToFetchDataFrom, range.start(), range.end());
        if (DataAccessGuard.hasAccessLevel(user, MagicLinkAccessLevel.ONLY_LAST_LOCATION)) {
            return new MapMetadata(0, 0, 0, 0, 0, 0, 0, metadata.latestLocation());
        }
        if (DataAccessGuard.hasAccessLevel(user, MagicLinkAccessLevel.MEMORY_VIEW_ONLY) || DataAccessGuard.hasAccessLevel(user, MagicLinkAccessLevel.MEMORY_EDIT_ACCESS)) {
            // the current location of the owner is not part of the memory
            return new MapMetadata(metadata.minTimestamp(), metadata.maxTimestamp(), metadata.totalPoints(),
                                   metadata.minLat(), metadata.maxLat(), metadata.minLng(), metadata.maxLng(), Optional.empty());
        }
        return metadata;
    }

    @GetMapping("/metadata/{userId}/device/{deviceId}")
    public MapMetadata getForDevice(@AuthenticationPrincipal User user,
                           @PathVariable Long userId,
                           @PathVariable Long deviceId,
                           @RequestParam String start,
                           @RequestParam String end,
                           @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone) {
        User userToFetchDataFrom = dataAccessGuard.loadUserToFetchDataFrom(user, userId);
        TimeWindow range = readableRange(user, start, end, timezone, MagicLinkAccessLevel.FULL_ACCESS);
        Device device = deviceJdbcService.find(userToFetchDataFrom, deviceId).orElseThrow(() -> new IllegalArgumentException("Device not found"));
        return this.sourceLocationPointJdbcService.getMetadata(userToFetchDataFrom, device, range.start(), range.end());
    }

    @GetMapping(value = "/stream/{userId}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<ResponseBodyEmitter> stream(
            @AuthenticationPrincipal User user,
            @PathVariable Long userId,
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone) {
        User userToFetchDataFrom = dataAccessGuard.loadUserToFetchDataFrom(user, userId);
        TimeWindow range = readableRange(user, start, end, timezone,
                MagicLinkAccessLevel.FULL_ACCESS, MagicLinkAccessLevel.ONLY_LIVE, MagicLinkAccessLevel.ONLY_LIVE_WITH_PHOTOS,
                MagicLinkAccessLevel.MEMORY_VIEW_ONLY, MagicLinkAccessLevel.MEMORY_EDIT_ACCESS);
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(0L);

        CompletableFuture.runAsync(() -> {
            try {
                streamingRawLocationPointJdbcService.streamPoints(userToFetchDataFrom, range.start(), range.end(), emitter);
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
            @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone) {
        User userToFetchDataFrom = dataAccessGuard.loadUserToFetchDataFrom(user, userId);
        TimeWindow range = readableRange(user, start, end, timezone, MagicLinkAccessLevel.FULL_ACCESS);
        Device device = deviceJdbcService.find(userToFetchDataFrom, deviceId).orElseThrow(() -> new IllegalArgumentException("Device not found"));

        ResponseBodyEmitter emitter = new ResponseBodyEmitter(0L);

        CompletableFuture.runAsync(() -> {
            try {
                streamingRawLocationPointJdbcService.streamPoints(userToFetchDataFrom, device, range.start(), range.end(), emitter);
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
        dataAccessGuard.readableWindow(user, timezone, MagicLinkAccessLevel.FULL_ACCESS);
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
        dataAccessGuard.readableWindow(user, timezone, MagicLinkAccessLevel.FULL_ACCESS);
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

    /**
     * Parses the requested range (the end is inclusive) and restricts it to what the caller may read.
     */
    private TimeWindow readableRange(User user, String start, String end, ZoneId timezone, MagicLinkAccessLevel... allowedLinkLevels) {
        Optional<TimeWindow> window = dataAccessGuard.readableWindow(user, timezone, allowedLinkLevels);
        Instant startInstant = parseInstant(start, timezone, false);
        Instant endInstant = parseInstant(end, timezone, true).plus(1, ChronoUnit.SECONDS);
        return window.map(w -> w.restrict(startInstant, endInstant)).orElse(new TimeWindow(startInstant, endInstant));
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
