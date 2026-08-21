package com.dedicatedcode.reitti.controller;

import java.util.List;

public class TransportModeUpdateRequest {
    private String returnUrl;
    private List<TransportModeSegmentUpdate> segments;

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public List<TransportModeSegmentUpdate> getSegments() {
        return segments;
    }

    public void setSegments(List<TransportModeSegmentUpdate> segments) {
        this.segments = segments;
    }
}