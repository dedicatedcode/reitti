package com.dedicatedcode.reitti.service.importer.dto.ios;

import com.fasterxml.jackson.annotation.JsonProperty;

public class IOSVisit {
    
    @JsonProperty("hierarchyLevel")
    private Integer hierarchyLevel;
    
    @JsonProperty("probability")
    private Double probability;
    
    @JsonProperty("topCandidate")
    private IOSTopCandidate topCandidate;
    
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
    
    public IOSTopCandidate getTopCandidate() {
        return topCandidate;
    }
    
    public void setTopCandidate(IOSTopCandidate topCandidate) {
        this.topCandidate = topCandidate;
    }
}
