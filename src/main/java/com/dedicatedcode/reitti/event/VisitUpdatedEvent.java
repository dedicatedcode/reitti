package com.dedicatedcode.reitti.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class VisitUpdatedEvent {
    private final String username;
    private final List<Long> visitIds;
    public VisitUpdatedEvent(
            @JsonProperty String username,
            @JsonProperty List<Long> visitIds) {
        this.username = username;
        this.visitIds = visitIds;
    }

    public String getUsername() {
        return username;
    }

    public List<Long> getVisitIds() {
        return visitIds;
    }
}
