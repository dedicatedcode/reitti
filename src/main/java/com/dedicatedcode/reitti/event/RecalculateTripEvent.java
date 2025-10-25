package com.dedicatedcode.reitti.event;

import java.io.Serializable;

public class RecalculateTripEvent implements Serializable {
    private final String username;
    private final long tripId;

    public RecalculateTripEvent(String username, long tripId) {
        this.username = username;
        this.tripId = tripId;
    }

    public String getUsername() {
        return username;
    }

    public long getTripId() {
        return tripId;
    }
}
