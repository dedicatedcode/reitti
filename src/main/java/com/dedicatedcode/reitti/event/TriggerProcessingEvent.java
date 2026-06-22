package com.dedicatedcode.reitti.event;

import com.dedicatedcode.reitti.service.JobContext;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public class TriggerProcessingEvent extends JobContext<TriggerProcessingEvent> implements Serializable {
    private final String username;
    private final String previewId;
    private final Instant receivedAt;
    private final String traceId;

    public TriggerProcessingEvent(
            String username,
            String previewId,
            String traceId) {
        this(username, previewId, traceId, null, null);
    }

    public TriggerProcessingEvent(
            String username,
            String previewId,
            String traceId,
            UUID jobId,
            UUID parentJobId) {
        super(jobId, parentJobId);
        this.username = username;
        this.previewId = previewId;
        this.traceId = traceId;
        this.receivedAt = Instant.now();
    }

    public String getUsername() {
        return username;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getPreviewId() {
        return this.previewId;
    }

    public String getTraceId() {
        return traceId;
    }

    @Override
    public TriggerProcessingEvent withJobId(UUID jobId) {
        return new  TriggerProcessingEvent(username, previewId, traceId, jobId, parentJobId);
    }

    @Override
    public TriggerProcessingEvent withParentJobId(UUID parentJobId) {
        return new   TriggerProcessingEvent(username, previewId, traceId, jobId, parentJobId);
    }

    @Override
    public String toString() {
        return "TriggerProcessingEvent{" +
                "username='" + username + '\'' +
                ", previewId='" + previewId + '\'' +
                ", receivedAt=" + receivedAt +
                ", traceId='" + traceId + '\'' +
                '}';
    }
}
