package com.dedicatedcode.reitti.event;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ProcessedVisitCreatedEvent {
    private final String username;
    private final long visitId;
    private final String previewId;

    public ProcessedVisitCreatedEvent(
            @JsonProperty String username,
            @JsonProperty long visitId,
            @JsonProperty String previewId) {
        this.username = username;
        this.visitId = visitId;
        this.previewId = previewId;
    }

    public String getUsername() {
        return username;
    }

    public long getVisitId() {
        return visitId;
    }

    public String getPreviewId() {
        return previewId;
    }
}
