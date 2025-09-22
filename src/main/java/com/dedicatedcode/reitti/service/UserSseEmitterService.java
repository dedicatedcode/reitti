package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.event.SSEEvent;
import com.dedicatedcode.reitti.event.SSEType;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.integration.ReittiIntegrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
public class UserSseEmitterService implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(UserSseEmitterService.class);
    private final ReittiIntegrationService reittiIntegrationService;
    private final Map<User, Set<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    public UserSseEmitterService(ReittiIntegrationService reittiIntegrationService) {
        this.reittiIntegrationService = reittiIntegrationService;
    }

    public SseEmitter addEmitter(User user) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        userEmitters.computeIfAbsent(user, _ -> new CopyOnWriteArraySet<>()).add(emitter);
        emitter.onCompletion(() -> {
            log.debug("SSE connection completed for user: [{}]", user);
            removeEmitter(user, emitter);
        });

        emitter.onTimeout(() -> {
            log.debug("SSE connection timed out for user: [{}]", user);
            emitter.complete();
            removeEmitter(user, emitter);
        });

        emitter.onError(throwable -> {
            log.error("SSE connection error for user [{}]: {}", user, throwable.getMessage());
            removeEmitter(user, emitter);
        });
        try {
            emitter.send(SseEmitter.event().data(new SSEEvent(SSEType.CONNECTED, null, null, null, null)));
        } catch (IOException e) {
            log.error("Unable to send initial event for user [{}]", user, e);
        }
        log.info("Emitter added for user: {}. Total emitters for user: {}", user, userEmitters.get(user).size());
        return emitter;
    }


    public void sendEventToUser(User user, SSEEvent eventData) {
        Set<SseEmitter> emitters = userEmitters.get(user);
        if (emitters != null) {
            for (SseEmitter emitter : new CopyOnWriteArraySet<>(emitters)) {
                try {
                    emitter.send(SseEmitter.event().data(eventData));
                    log.debug("Sent event to user: {}", user);
                } catch (IOException e) {
                    log.error("Error sending event to user {}: {}", user, e.getMessage());
                    emitter.completeWithError(e);
                    removeEmitter(user, emitter);
                }
            }
        } else {
            log.debug("No active SSE emitters for user: {}", user);
        }
    }

    private void removeEmitter(User user, SseEmitter emitter) {
        Set<SseEmitter> emitters = userEmitters.get(user);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                    userEmitters.remove(user);
                    reittiIntegrationService.unsubscribeFromIntegrations(user);
                }
            log.info("Emitter removed for user: {}. Remaining emitters for user: {}", user, userEmitters.containsKey(user) ? userEmitters.get(user).size() : 0);
        }
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
        userEmitters.values().forEach(sseEmitters ->  sseEmitters.forEach(SseEmitter::complete));
    }

    @Override
    public boolean isRunning() {
        return true;
    }
}