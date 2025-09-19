package com.dedicatedcode.reitti.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

public class LocationProcessEvent implements Serializable {
    private final String username;
    private final Instant earliest;
    private final Instant latest;
    private final String previewId;

    @JsonCreator
    public LocationProcessEvent(
            @JsonProperty("username") String username,
            @JsonProperty("earliest") Instant earliest,
            @JsonProperty("latest") Instant latest,
            @JsonProperty("previewId") String previewId) {
        this.username = username;
        this.earliest = earliest;
        this.latest = latest;
        this.previewId = previewId;
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

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        LocationProcessEvent that = (LocationProcessEvent) o;
        return Objects.equals(username, that.username) && Objects.equals(earliest, that.earliest) && Objects.equals(latest, that.latest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, earliest, latest);
    }
}
