package com.dedicatedcode.reitti.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class ImmichAssetUpdateRequest {

    @JsonProperty("ids")
    private final List<String> ids;

    @JsonProperty("latitude")
    private final Double latitude;

    @JsonProperty("longitude")
    private final Double longitude;

    public ImmichAssetUpdateRequest(List<String> ids, Double latitude, Double longitude) {
        this.ids = ids;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public List<String> getIds() {
        return ids;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }
}