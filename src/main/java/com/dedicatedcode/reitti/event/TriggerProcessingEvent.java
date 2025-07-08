package com.dedicatedcode.reitti.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.Instant;

public class TriggerProcessingEvent implements Serializable {
    private final String username;
    private final Instant receivedAt;

    @JsonCreator
    public TriggerProcessingEvent(
            @JsonProperty("username") String username) {
        this.username = username;
        this.receivedAt = Instant.now();
    }

    public String getUsername() {
        return username;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
