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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class UserSseEmitterService implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(UserSseEmitterService.class);
    private static final long HEARTBEAT_INTERVAL_SECONDS = 30;
    private final ReittiIntegrationService reittiIntegrationService;
    private final Map<Long, Set<SseEmitter>> userEmitters = new ConcurrentHashMap<>();
    private final Map<SseEmitter, User> emitterOwners = new ConcurrentHashMap<>();
    private final Set<SseEmitter> integrationEmitters = ConcurrentHashMap.newKeySet();
    private final LinkedBlockingQueue<TaskData> queue = new LinkedBlockingQueue<>();
    private volatile boolean running;
    private Thread drainThread;
    private ScheduledExecutorService heartbeatExecutor;

    public UserSseEmitterService(ReittiIntegrationService reittiIntegrationService) {
        this.reittiIntegrationService = reittiIntegrationService;
    }

    public SseEmitter addEmitter(User user) {
        return addEmitter(user, true);
    }

    public SseEmitter addEmitter(User user, boolean integrationSubscribed) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        userEmitters.computeIfAbsent(user.getId(), _ -> new CopyOnWriteArraySet<>()).add(emitter);
        emitterOwners.put(emitter, user);
        if (integrationSubscribed) {
            integrationEmitters.add(emitter);
        }
        emitter.onCompletion(() -> {
            log.trace("SSE connection completed for user: [{}]", user);
            removeEmitter(user, emitter);
        });

        emitter.onTimeout(() -> {
            log.trace("SSE connection timed out for user: [{}]", user);
            emitter.complete();
            removeEmitter(user, emitter);
        });

        emitter.onError(throwable -> {
            log.trace("SSE connection error for user [{}]: {}", user, throwable.getMessage());
            removeEmitter(user, emitter);
        });
        try {
            emitter.send(SseEmitter.event().data(new SSEEvent(SSEType.CONNECTED, null, null, null, null)));
        } catch (IOException e) {
            log.error("Unable to send initial event for user [{}]", user, e);
            removeEmitter(user, emitter);
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // already completed or errored
            }
        }
        log.trace("Emitter added for user: {}. Total emitters for user: {}", user, userEmitters.get(user.getId()).size());
        return emitter;
    }

    public void sendEventToUser(User user, SSEEvent eventData) {
        queue.add(new TaskData(user, eventData));
    }

    public void sendEventToUser(User user, Object eventData) {
        queue.add(new TaskData(user, eventData));
    }

    private void sendToEmitters(User user, Object eventData) {
        Set<SseEmitter> emitters = userEmitters.get(user.getId());
        if (emitters != null) {
            for (SseEmitter emitter : new CopyOnWriteArraySet<>(emitters)) {
                if (!emitters.contains(emitter)) {
                    continue;
                }
                try {
                    emitter.send(SseEmitter.event().data(eventData));
                    log.trace("Sent event to user: {}", user);
                } catch (Exception e) {
                    log.debug("Error sending event to user {}: {}", user, e.getMessage());
                    removeEmitter(user, emitter);
                    try {
                        emitter.completeWithError(e);
                    } catch (Exception ignored) {
                        // The AsyncContext may already be in an error state; nothing more to do.
                    }
                }
            }
        }
    }

    private void sendHeartbeats() {
        for (Map.Entry<SseEmitter, User> entry : emitterOwners.entrySet()) {
            SseEmitter emitter = entry.getKey();
            User user = entry.getValue();
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (Exception e) {
                log.trace("SSE heartbeat failed for user [{}]: {}", user, e.getMessage());
                removeEmitter(user, emitter);
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {
                    // already completed or errored
                }
            }
        }
    }

    private void removeEmitter(User user, SseEmitter emitter) {
        emitterOwners.remove(emitter);
        Set<SseEmitter> emitters = userEmitters.get(user.getId());
        if (emitters != null) {
            emitters.remove(emitter);
            boolean wasIntegrationEmitter = integrationEmitters.remove(emitter);
            boolean stillIntegrationEmitter = emitters.stream().anyMatch(integrationEmitters::contains);
            if (emitters.isEmpty()) {
                userEmitters.remove(user.getId());
            }
            if (wasIntegrationEmitter && !stillIntegrationEmitter) {
                reittiIntegrationService.unsubscribeFromIntegrations(user);
            }
            log.trace("Emitter removed for user: {}. Remaining emitters for user: {}", user, userEmitters.getOrDefault(user.getId(), Collections.emptySet()).size());
        }
    }

    @Override
    public void start() {
        running = true;
        drainThread = new Thread(() -> {
            while (running) {
                try {
                    TaskData data = queue.take();
                    sendToEmitters(data.user, data.eventData);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            log.info("SSE drain thread stopped");
        }, "sse-drain");
        drainThread.setDaemon(true);
        drainThread.start();

        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sse-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        heartbeatExecutor.scheduleAtFixedRate(this::sendHeartbeats,
                HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        running = false;
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
            heartbeatExecutor = null;
        }
        if (drainThread != null) {
            drainThread.interrupt();
        }
        userEmitters.values().forEach(sseEmitters -> sseEmitters.forEach(SseEmitter::complete));
        emitterOwners.clear();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private record TaskData(User user, Object eventData) {}
}
