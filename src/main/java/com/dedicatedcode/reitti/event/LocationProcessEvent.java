package com.dedicatedcode.reitti.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class LocationProcessEvent implements Serializable {
    private final String username;
    private final Instant earliest;
    private final Instant latest;
    private final String previewId;
    private final String traceId;

    @JsonCreator
    public LocationProcessEvent(
            @JsonProperty("username") String username,
            @JsonProperty("earliest") Instant earliest,
            @JsonProperty("latest") Instant latest,
            @JsonProperty("previewId") String previewId,
            @JsonProperty("trace-id") String traceId) {
        this.username = username;
        this.earliest = earliest;
        this.latest = latest;
        this.previewId = previewId;
        this.traceId = traceId;
    }

    public String getUsername() {
        return username;
    }

    public Instant getEarliest() {
        return earliest;
    }

    public Instant getLatest() {
        return latest;
    }

    public String getPreviewId() {
        return previewId;
    }

    public String getTraceId() {
        return traceId;
    }

    @Override
    public String toString() {
        return "LocationProcessEvent{" +
                "username='" + username + '\'' +
                ", earliest=" + earliest +
                ", latest=" + latest +
                ", previewId='" + previewId + '\'' +
                ", traceId='" + traceId + '\'' +
                '}';
    }
}
