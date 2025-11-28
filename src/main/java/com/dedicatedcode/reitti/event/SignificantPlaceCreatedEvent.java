package com.dedicatedcode.reitti.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public final class SignificantPlaceCreatedEvent implements Serializable {
    @Serial
    private static final long serialVersionUID = 0L;
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
                                        @JsonProperty("trace-id") String traceId) {
        this.username = username;
        this.previewId = previewId;
        this.placeId = placeId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.traceId = traceId;
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
}
