package com.dedicatedcode.reitti.event;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.model.RawLocationPoint;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public class LocationProcessEvent implements Serializable {
    private final String username;
    private final Instant earliest;
    private final Instant latest;

    @JsonCreator
    public LocationProcessEvent(
            @JsonProperty("username") String username,
            @JsonProperty("earliest") Instant earliest,
            @JsonProperty("latest") Instant latest) {
        this.username = username;
        this.earliest = earliest;
        this.latest = latest;
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
}
