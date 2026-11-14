package com.dedicatedcode.reitti.controller.api.v3;

import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.DeviceJdbcService;
import com.dedicatedcode.reitti.service.StreamingRawLocationPointJdbcService;
import com.dedicatedcode.reitti.service.StreamingRawLocationPointJdbcService.StreamFormat;
import com.dedicatedcode.reitti.service.processing.TimeRange;
import com.dedicatedcode.reitti.service.security.DataAccessGuard;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v3/locations")
public class LocationStreamApiController {

    private final DataAccessGuard dataAccessGuard;
    private final DeviceJdbcService deviceJdbcService;
    private final StreamingRawLocationPointJdbcService streamingRawLocationPointJdbcService;

    public LocationStreamApiController(DataAccessGuard dataAccessGuard,
                                       DeviceJdbcService deviceJdbcService,
                                       StreamingRawLocationPointJdbcService streamingRawLocationPointJdbcService) {
        this.dataAccessGuard = dataAccessGuard;
        this.deviceJdbcService = deviceJdbcService;
        this.streamingRawLocationPointJdbcService = streamingRawLocationPointJdbcService;
    }

    @GetMapping(value = "/stream/{userId}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<ResponseBodyEmitter> stream(
            @AuthenticationPrincipal User user,
            @PathVariable Long userId,
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone) throws IllegalAccessException {
        User userToFetchDataFrom = this.dataAccessGuard.loadUserToFetchDataFrom(user, userId);
        this.dataAccessGuard.requireHistoryAccess(user, userToFetchDataFrom);
        TimeRange timeRange = this.dataAccessGuard.loadTimeRange(user, start, end, timezone);
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(0L);

        CompletableFuture.runAsync(() -> {
            try {
                streamingRawLocationPointJdbcService.streamPoints(userToFetchDataFrom, timeRange.start(), timeRange.end().plus(1, ChronoUnit.SECONDS), StreamFormat.V3_INT32_SECONDS, emitter);
            } catch (Exception e) {
                completeWithError(emitter, e);
            }
        });

        return streamResponse(emitter);
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
        this.dataAccessGuard.requireHistoryAccess(user, userToFetchDataFrom);
        TimeRange timeRange = this.dataAccessGuard.loadTimeRange(user, start, end, timezone);

        Device device = deviceJdbcService.find(userToFetchDataFrom, deviceId).orElseThrow(() -> new IllegalArgumentException("Device not found"));

        ResponseBodyEmitter emitter = new ResponseBodyEmitter(0L);

        CompletableFuture.runAsync(() -> {
            try {
                streamingRawLocationPointJdbcService.streamPoints(userToFetchDataFrom, device, timeRange.start(), timeRange.end().plus(1, ChronoUnit.SECONDS), StreamFormat.V3_INT32_SECONDS, emitter);
            } catch (Exception e) {
                completeWithError(emitter, e);
            }
        });

        return streamResponse(emitter);
    }

    private static ResponseEntity<ResponseBodyEmitter> streamResponse(ResponseBodyEmitter emitter) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .header(HttpHeaders.CONTENT_ENCODING, "identity")
                .body(emitter);
    }

    private static void completeWithError(ResponseBodyEmitter emitter, Exception e) {
        if (e.getCause() instanceof java.io.IOException) {
            try { emitter.complete(); } catch (Exception ignored) {}
        } else {
            try { emitter.completeWithError(e); } catch (Exception ignored) {}
        }
    }
}
