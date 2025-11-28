package com.dedicatedcode.reitti.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public class TriggerProcessingEvent implements Serializable {
    private final String username;
    private final String previewId;
    private final Instant receivedAt;
    private final String traceId;

    @JsonCreator
    public TriggerProcessingEvent(
            @JsonProperty("username") String username,
            String previewId,
            @JsonProperty("trace-id") String traceId) {
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
    public String toString() {
        return "TriggerProcessingEvent{" +
                "username='" + username + '\'' +
                ", previewId='" + previewId + '\'' +
                ", receivedAt=" + receivedAt +
                ", traceId='" + traceId + '\'' +
                '}';
    }
}
