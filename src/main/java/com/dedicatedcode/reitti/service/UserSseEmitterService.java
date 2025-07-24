package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.event.SSEEvent;
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
    private final Map<Long, Set<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    public SseEmitter addEmitter(Long userId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(emitter);
        emitter.onCompletion(() -> {
            log.info("SSE connection completed for user: [{}]", userId);
            removeEmitter(userId, emitter);
        });

        emitter.onTimeout(() -> {
            log.info("SSE connection timed out for user: [{}]", userId);
            emitter.complete(); // Complete the emitter on timeout
            removeEmitter(userId, emitter);
        });

        emitter.onError(throwable -> {
            log.error("SSE connection error for user [{}]: {}", userId, throwable.getMessage());
            removeEmitter(userId, emitter);
        });
        log.info("Emitter added for user: {}. Total emitters for user: {}", userId, userEmitters.get(userId).size());
        return emitter;
    }


    public void sendEventToUser(Long userId, SSEEvent eventData) {
        Set<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters != null) {
            for (SseEmitter emitter : new CopyOnWriteArraySet<>(emitters)) {
                try {
                    emitter.send(SseEmitter.event().data(eventData));
                    log.info("Sent event to user: {}", userId);
                } catch (IOException e) {
                    log.error("Error sending event to user {}: {}", userId, e.getMessage());
                    emitter.completeWithError(e);
                    removeEmitter(userId, emitter);
                }
            }
        } else {
            System.out.println("No active SSE emitters for user: " + userId);
        }
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        Set<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                userEmitters.remove(userId);
            }
            log.info("Emitter removed for user: {}. Remaining emitters for user: {}", userId, userEmitters.containsKey(userId) ? userEmitters.get(userId).size() : 0);
        }
    }

    public void removeEmitter(Long userId) {
        Set<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters != null) {
            for (SseEmitter emitter : emitters) {
                removeEmitter(userId, emitter);
            }
            userEmitters.remove(userId);
            log.info("Removed all emitters for user: {}. Remaining emitters for user: {}", userId, userEmitters.containsKey(userId) ? userEmitters.get(userId).size() : 0);
        }
    }

    public void sendEventToAllUsers(Object eventData) {
        userEmitters.forEach((userId, emitters) -> {
            for (SseEmitter emitter : new CopyOnWriteArraySet<>(emitters)) {
                try {
                    emitter.send(SseEmitter.event().data(eventData));
                } catch (IOException e) {
                    emitter.completeWithError(e);
                    removeEmitter(userId, emitter);
                }
            }
        });
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