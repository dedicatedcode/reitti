package com.dedicatedcode.reitti.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public record SignificantPlaceCreatedEvent(String username, String previewId, Long placeId, Double latitude,
                                           Double longitude, String traceId) implements Serializable {
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
