package com.dedicatedcode.reitti.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

public class RecalculateTripEvent implements Serializable {
    private final String username;
    private final long tripId;
    private final String traceId;

    public RecalculateTripEvent(String username, long tripId,
                                @JsonProperty("trace-id") String traceId) {
        this.username = username;
        this.tripId = tripId;
        this.traceId = traceId;
    }

    public String getUsername() {
        return username;
    }

    public long getTripId() {
        return tripId;
    }

    @Override
    public String toString() {
        return "RecalculateTripEvent{" +
                "username='" + username + '\'' +
                ", tripId=" + tripId +
                ", traceId='" + traceId + '\'' +
                '}';
    }
}
