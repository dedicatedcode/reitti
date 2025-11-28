package com.dedicatedcode.reitti.event;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ProcessedVisitCreatedEvent {
    private final String username;
    private final long visitId;
    private final String previewId;
    private final String traceId;

    public ProcessedVisitCreatedEvent(
            @JsonProperty String username,
            @JsonProperty long visitId,
            @JsonProperty String previewId,
            @JsonProperty("trace-id") String traceId) {
        this.username = username;
        this.visitId = visitId;
        this.previewId = previewId;
        this.traceId = traceId;
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

    public String getTraceId() {
        return traceId;
    }

    @Override
    public String toString() {
        return "ProcessedVisitCreatedEvent{" +
                "username='" + username + '\'' +
                ", visitId=" + visitId +
                ", previewId='" + previewId + '\'' +
                ", traceId='" + traceId + '\'' +
                '}';
    }
}
