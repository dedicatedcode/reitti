package com.dedicatedcode.reitti.service.importer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties({"userLocationProfile"})
public class GoogleTimelineData {
    
    @JsonProperty("semanticSegments")
    private List<SemanticSegment> semanticSegments;
    
    @JsonProperty("rawSignals")
    private List<RawSignal> rawSignals;

    public List<SemanticSegment> getSemanticSegments() {
        return semanticSegments;
    }
    
    public void setSemanticSegments(List<SemanticSegment> semanticSegments) {
        this.semanticSegments = semanticSegments;
    }
    
    public List<RawSignal> getRawSignals() {
        return rawSignals;
    }
    
    public void setRawSignals(List<RawSignal> rawSignals) {
        this.rawSignals = rawSignals;
    }
}
