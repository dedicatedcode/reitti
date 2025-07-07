package com.dedicatedcode.reitti.service.importer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Visit {
    
    @JsonProperty("hierarchyLevel")
    private Integer hierarchyLevel;
    
    @JsonProperty("probability")
    private Double probability;
    
    @JsonProperty("topCandidate")
    private TopCandidate topCandidate;
    
    public Integer getHierarchyLevel() {
        return hierarchyLevel;
    }
    
    public void setHierarchyLevel(Integer hierarchyLevel) {
        this.hierarchyLevel = hierarchyLevel;
    }
    
    public Double getProbability() {
        return probability;
    }
    
    public void setProbability(Double probability) {
        this.probability = probability;
    }
    
    public TopCandidate getTopCandidate() {
        return topCandidate;
    }
    
    public void setTopCandidate(TopCandidate topCandidate) {
        this.topCandidate = topCandidate;
    }
}
