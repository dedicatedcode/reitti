package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.geo.TransportMode;

public class TransportModeSegmentUpdate {
    private long offsetSeconds;
    private TransportMode transportMode;

    public long getOffsetSeconds() {
        return offsetSeconds;
    }

    public void setOffsetSeconds(long offsetSeconds) {
        this.offsetSeconds = offsetSeconds;
    }

    public TransportMode getTransportMode() {
        return transportMode;
    }

    public void setTransportMode(TransportMode transportMode) {
        this.transportMode = transportMode;
    }
}