package com.dedicatedcode.reitti.service.importer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TopCandidate {
    
    @JsonProperty("placeId")
    private String placeId;
    
    @JsonProperty("semanticType")
    private String semanticType;
    
    @JsonProperty("probability")
    private Double probability;
    
    @JsonProperty("placeLocation")
    private PlaceLocation placeLocation;
    
    @JsonProperty("type")
    private String type;
    
    public String getPlaceId() {
        return placeId;
    }
    
    public void setPlaceId(String placeId) {
        this.placeId = placeId;
    }
    
    public String getSemanticType() {
        return semanticType;
    }
    
    public void setSemanticType(String semanticType) {
        this.semanticType = semanticType;
    }
    
    public Double getProbability() {
        return probability;
    }
    
    public void setProbability(Double probability) {
        this.probability = probability;
    }
    
    public PlaceLocation getPlaceLocation() {
        return placeLocation;
    }
    
    public void setPlaceLocation(PlaceLocation placeLocation) {
        this.placeLocation = placeLocation;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
}
