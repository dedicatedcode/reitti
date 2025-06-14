package com.dedicatedcode.reitti.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ImmichSearchRequest {
    
    @JsonProperty("takenAfter")
    private String takenAfter;
    
    @JsonProperty("takenBefore") 
    private String takenBefore;
    
    @JsonProperty("type")
    private String type = "IMAGE";
    
    @JsonProperty("withExif")
    private boolean withExif = true;
    
    @JsonProperty("size")
    private int size = 1000;
    
    @JsonProperty("page")
    private int page = 1;

    public ImmichSearchRequest() {}

    public ImmichSearchRequest(String takenAfter, String takenBefore) {
        this.takenAfter = takenAfter;
        this.takenBefore = takenBefore;
    }

    public String getTakenAfter() {
        return takenAfter;
    }

    public void setTakenAfter(String takenAfter) {
        this.takenAfter = takenAfter;
    }

    public String getTakenBefore() {
        return takenBefore;
    }

    public void setTakenBefore(String takenBefore) {
        this.takenBefore = takenBefore;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isWithExif() {
        return withExif;
    }

    public void setWithExif(boolean withExif) {
        this.withExif = withExif;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }
}
