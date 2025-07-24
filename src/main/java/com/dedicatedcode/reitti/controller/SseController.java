package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.service.UserSseEmitterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class SseController {
    private static final Logger log = LoggerFactory.getLogger(SseController.class);
    private final UserSseEmitterService emitterService;

    public SseController(UserSseEmitterService userSseEmitterService) {
        this.emitterService = userSseEmitterService;
    }

    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter handleSseForUser(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            throw new IllegalStateException("User not authenticated for SSE endpoint.");
        }

        User user = (User) userDetails;
        SseEmitter emitter = emitterService.addEmitter(user.getId());
        log.info("New SSE connection from user: [{}]", user.getId());
        return emitter;
    }
}