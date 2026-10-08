package com.dedicatedcode.reitti.controller.api.v2;

import com.dedicatedcode.reitti.controller.error.ForbiddenException;
import com.dedicatedcode.reitti.dto.MapMetadata;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.MagicLinkAccessLevel;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.DeviceJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.SourceLocationPointJdbcService;
import com.dedicatedcode.reitti.service.GeoJsonExportService;
import com.dedicatedcode.reitti.service.StreamingRawLocationPointJdbcService;
import com.dedicatedcode.reitti.service.processing.TimeRange;
import com.dedicatedcode.reitti.service.security.DataAccessGuard;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
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
                           @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone) throws IllegalAccessException {
        User userToFetchDataFrom = this.dataAccessGuard.loadUserToFetchDataFrom(user, userId);
        TimeRange timeRange = this.dataAccessGuard.loadTimeRange(user, start, end, timezone);

        MapMetadata metadata = this.jdbcService.getMetadata(userToFetchDataFrom, timeRange.start(), timeRange.end().plus(1, ChronoUnit.SECONDS));
        return cleanUpByAccessLevel(user, metadata);
    }

    @GetMapping("/metadata/{userId}/device/{deviceId}")
    public MapMetadata getForDevice(@AuthenticationPrincipal User user,
                           @PathVariable Long userId,
                           @PathVariable Long deviceId,
                           @RequestParam String start,
                           @RequestParam String end,
                           @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone) {
        User userToFetchDataFrom = this.dataAccessGuard.loadUserToFetchDataFrom(user, userId);
        if (!userToFetchDataFrom.equals(user)) {
            throw new ForbiddenException("Users cant load device data from other users");
        }

        TimeRange timeRange = this.dataAccessGuard.loadTimeRange(user, start, end, timezone);
        Device device = deviceJdbcService.find(userToFetchDataFrom, deviceId).orElseThrow(() -> new IllegalArgumentException("Device not found"));
        MapMetadata metadata = this.sourceLocationPointJdbcService.getMetadata(userToFetchDataFrom, device, timeRange.start(), timeRange.end().plus(1, ChronoUnit.SECONDS));
        return cleanUpByAccessLevel(user, metadata);
    }

    @GetMapping(value = "/stream/{userId}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<ResponseBodyEmitter> stream(
            @AuthenticationPrincipal User user,
            @PathVariable Long userId,
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone) throws IllegalAccessException {
        User userToFetchDataFrom = this.dataAccessGuard.loadUserToFetchDataFrom(user, userId);
        TimeRange timeRange = this.dataAccessGuard.loadTimeRange(user, start, end, timezone);
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(0L);

        CompletableFuture.runAsync(() -> {
            try {
                streamingRawLocationPointJdbcService.streamPoints(userToFetchDataFrom, timeRange.start(), timeRange.end().plus(1, ChronoUnit.SECONDS), emitter);
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
        User userToFetchDataFrom = this.dataAccessGuard.loadUserToFetchDataFrom(user, userId);
        TimeRange timeRange = this.dataAccessGuard.loadTimeRange(user, start, end, timezone);

        Device device = deviceJdbcService.find(userToFetchDataFrom, deviceId).orElseThrow(() -> new IllegalArgumentException("Device not found"));

        ResponseBodyEmitter emitter = new ResponseBodyEmitter(0L);

        CompletableFuture.runAsync(() -> {
            try {
                streamingRawLocationPointJdbcService.streamPoints(userToFetchDataFrom, device, timeRange.start(), timeRange.end().plus(1, ChronoUnit.SECONDS), emitter);
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
            TimeRange timeRange = this.dataAccessGuard.loadTimeRange(user, start, end, timezone);

            StreamingResponseBody stream = outputStream -> {
                try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                    geoJsonExportService.generateGeoJsonContentStreaming(
                            user,
                            timeRange.start(),
                            timeRange.end().plus(1, ChronoUnit.SECONDS),
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
            TimeRange timeRange = this.dataAccessGuard.loadTimeRange(user, start, end, timezone);

            StreamingResponseBody stream = outputStream -> {
                try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                    geoJsonExportService.generateGeoJsonContentStreaming(
                            user,
                            timeRange.start(),
                            timeRange.end().plus(1, ChronoUnit.SECONDS),
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

    private static MapMetadata cleanUpByAccessLevel(User user, MapMetadata metadata) {
        Collection<? extends GrantedAuthority> authorities = user.getAuthorities();
        if (authorities.contains(MagicLinkAccessLevel.ONLY_LAST_LOCATION.asAuthority())) {
            return new MapMetadata(0, 0, 0, 0, 0, 0, 0, metadata.latestLocation());
        } else if (authorities.contains(MagicLinkAccessLevel.MEMORY_VIEW_ONLY.asAuthority()) || authorities.contains(MagicLinkAccessLevel.MEMORY_EDIT_ACCESS.asAuthority())) {
            return new MapMetadata(metadata.minTimestamp(), metadata.maxTimestamp(), metadata.totalPoints(), metadata.minLat(), metadata.maxLat(), metadata.minLng(), metadata.maxLng(), Optional.empty());
        } else {
            return metadata;
        }
    }
}
