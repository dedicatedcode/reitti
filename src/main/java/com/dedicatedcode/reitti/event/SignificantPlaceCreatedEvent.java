package com.dedicatedcode.reitti.event;

import java.io.Serializable;

public class SignificantPlaceCreatedEvent implements Serializable {
    private final Long placeId;
    private final Double latitude;
    private final Double longitude;

    public SignificantPlaceCreatedEvent(Long placeId, Double latitude, Double longitude) {
        this.placeId = placeId;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Long getPlaceId() {
        return placeId;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }
}
