package com.dedicatedcode.reitti.event;

import com.dedicatedcode.reitti.service.JobContext;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public final class SignificantPlaceCreatedEvent extends JobContext<SignificantPlaceCreatedEvent> implements Serializable {
    private final String username;
    private final String previewId;
    private final Long placeId;
    private final Double latitude;
    private final Double longitude;
    private final String traceId;

    public SignificantPlaceCreatedEvent(String username,
                                        String previewId,
                                        Long placeId,
                                        Double latitude,
                                        Double longitude,
                                        String traceId,
                                        UUID jobId,
                                        UUID parentJobId) {
        super(jobId, parentJobId);
        this.username = username;
        this.previewId = previewId;
        this.placeId = placeId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.traceId = traceId;
    }

    public SignificantPlaceCreatedEvent(String username,
                                        String previewId,
                                        Long placeId,
                                        Double latitude,
                                        Double longitude,
                                        String traceId) {
        this(username, previewId, placeId, latitude, longitude, traceId, null, null);
    }

    @Override
    public String toString() {
        return "SignificantPlaceCreatedEvent{" +
                "username='" + username + '\'' +
                ", previewId='" + previewId + '\'' +
                ", placeId=" + placeId +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", traceId='" + traceId + '\'' +
                '}';
    }

    public String username() {
        return username;
    }

    public String previewId() {
        return previewId;
    }

    public Long placeId() {
        return placeId;
    }

    public Double latitude() {
        return latitude;
    }

    public Double longitude() {
        return longitude;
    }

    public String traceId() {
        return traceId;
    }

    @Override
    public SignificantPlaceCreatedEvent withJobId(UUID jobId) {
        return new SignificantPlaceCreatedEvent(username,  previewId, placeId, latitude, longitude, traceId, jobId, parentJobId);
    }

    @Override
    public SignificantPlaceCreatedEvent withParentJobId(UUID parentJobId) {
        return new SignificantPlaceCreatedEvent(username,  previewId, placeId, latitude, longitude, traceId, jobId, parentJobId);
    }
}
