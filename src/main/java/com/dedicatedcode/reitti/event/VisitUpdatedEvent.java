package com.dedicatedcode.reitti.event;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VisitUpdatedEvent {
    private final String username;
    private final long visitId;
    public VisitUpdatedEvent(
            @JsonProperty String username,
            @JsonProperty long visitId) {
        this.username = username;
        this.visitId = visitId;
    }

    public String getUsername() {
        return username;
    }

    public long getVisitId() {
        return visitId;
    }
}
