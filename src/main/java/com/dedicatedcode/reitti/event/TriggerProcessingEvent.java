package com.dedicatedcode.reitti.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.Instant;

public class TriggerProcessingEvent implements Serializable {
    private final String username;
    private final String previewId;
    private final Instant receivedAt;

    @JsonCreator
    public TriggerProcessingEvent(
            @JsonProperty("username") String username,
            String previewId) {
        this.username = username;
        this.previewId = previewId;
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
}
