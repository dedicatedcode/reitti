package com.dedicatedcode.reitti.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class VisitUpdatedEvent {
    private final String username;
    private final List<Long> visitIds;
    private final String previewId;

    public VisitUpdatedEvent(
            @JsonProperty String username,
            @JsonProperty List<Long> visitIds,
            @JsonProperty String previewId) {
        this.username = username;
        this.visitIds = visitIds;
        this.previewId = previewId;
    }

    public String getUsername() {
        return username;
    }

    public List<Long> getVisitIds() {
        return visitIds;
    }

    public String getPreviewId() {
        return previewId;
    }
}
