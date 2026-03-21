package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.dto.MapMetadata;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.integration.ReittiIntegrationService;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Controller
@RequestMapping("/reitti-integration")
public class ReittiIntegrationController {
    private final ReittiIntegrationService reittiIntegrationService;

    public ReittiIntegrationController(ReittiIntegrationService reittiIntegrationService) {
        this.reittiIntegrationService = reittiIntegrationService;
    }

    @GetMapping("/avatar/{integrationId}")
    public ResponseEntity<byte[]> getAvatar(@AuthenticationPrincipal User user, @PathVariable Long integrationId) {
        return this.reittiIntegrationService.getAvatar(user, integrationId).map(avatarData -> {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(avatarData.mimeType()));
            headers.setContentLength(avatarData.imageData().length);
            headers.setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS));
            return new ResponseEntity<>(avatarData.imageData(), headers, HttpStatus.OK);
        }).orElse(ResponseEntity.notFound().cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS)).build());
    }

    @GetMapping(value = "/metadata/{integrationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<MapMetadata> getMetadata(@AuthenticationPrincipal User user,
                                                   @PathVariable Long integrationId,
                                                   @RequestParam String start,
                                                   @RequestParam String end,
                                                   @RequestParam(required = false, defaultValue = "UTC") String timezone) {
        MapMetadata metadata = reittiIntegrationService.getMetadata(user, integrationId, start, end, timezone);
        if (metadata != null) {
            return ResponseEntity.ok(metadata);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping(value = "/stream/{integrationId}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<ResponseBodyEmitter> streamLocations(@AuthenticationPrincipal User user,
                                                               @PathVariable Long integrationId,
                                                               @RequestParam String start,
                                                               @RequestParam String end,
                                                               @RequestParam(required = false, defaultValue = "UTC") String timezone) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(0L);
        CompletableFuture.runAsync(() -> {
            try {
                reittiIntegrationService.streamLocations(user, integrationId, start, end, timezone, emitter);
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .header(HttpHeaders.CONTENT_ENCODING, "identity")
                .body(emitter);
    }

    @GetMapping(value = "/visits/{integrationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> getVisits(@AuthenticationPrincipal User user,
                                                  @PathVariable Long integrationId,
                                                  @RequestParam String startDate,
                                                  @RequestParam String endDate,
                                                  @RequestParam(required = false, defaultValue = "UTC") String timezone,
                                                  @RequestParam(required = false) Integer zoom) {
        return ResponseEntity.ok(reittiIntegrationService.getVisits(user, integrationId, startDate, endDate, zoom, timezone));
    }
}
