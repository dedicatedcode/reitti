package com.dedicatedcode.reitti.service.importer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ActivityLocation {
    
    @JsonProperty("latLng")
    private String latLng;
    
    public String getLatLng() {
        return latLng;
    }
    
    public void setLatLng(String latLng) {
        this.latLng = latLng;
    }
}
