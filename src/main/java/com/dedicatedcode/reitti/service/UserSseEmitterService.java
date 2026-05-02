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
import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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
                    log.trace("Sent event to user: {}", user);
                } catch (IOException e) {
                    log.error("Error sending event to user {}: {}", user, e.getMessage());
                    emitter.completeWithError(e);
                    removeEmitter(user, emitter);
                }
            }
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

    public static final class TaskData extends JobContext<TaskData> {
        private final User user;
        private final SSEEvent eventData;

        public TaskData(User user, SSEEvent eventData) {
            this(user, eventData, null,  null);
        }
        public TaskData(User user, SSEEvent eventData, UUID jobId, UUID parentJobId) {
            super(jobId, parentJobId);
            this.user = user;
            this.eventData = eventData;
        }

        public User user() {
            return user;
        }

        public SSEEvent eventData() {
            return eventData;
        }

        public UUID parentJobId() {
            return parentJobId;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (TaskData) obj;
            return Objects.equals(this.user, that.user) &&
                    Objects.equals(this.eventData, that.eventData) &&
                    Objects.equals(this.parentJobId, that.parentJobId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(user, eventData, parentJobId);
        }

        @Override
        public String toString() {
            return "TaskData[" +
                    "user=" + user + ", " +
                    "eventData=" + eventData + ", " +
                    "parentJobId=" + parentJobId + ']';
        }

        @Override
        public TaskData withJobId(UUID jobId) {
            return new TaskData(user, eventData, jobId, parentJobId);
        }

        @Override
        public TaskData withParentJobId(UUID parentJobId) {
            return new TaskData(user, eventData, jobId, parentJobId);
        }
    }
}