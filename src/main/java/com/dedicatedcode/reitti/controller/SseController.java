package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.service.UserSseEmitterService;
import com.dedicatedcode.reitti.service.integration.ReittiIntegrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class SseController {
    private static final Logger log = LoggerFactory.getLogger(SseController.class);
    private final UserSseEmitterService emitterService;
    private final ReittiIntegrationService reittiIntegrationService;

    public SseController(UserSseEmitterService userSseEmitterService, 
                        ReittiIntegrationService reittiIntegrationService) {
        this.emitterService = userSseEmitterService;
        this.reittiIntegrationService = reittiIntegrationService;
    }

    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter handleSseForUser(@AuthenticationPrincipal User user) {
        SseEmitter emitter = emitterService.addEmitter(user);
        reittiIntegrationService.registerSubscriptionsForUser(user);
        log.info("New SSE connection from user: [{}]", user.getId());
        return emitter;
    }
}
