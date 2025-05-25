package com.dedicatedcode.reitti.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class LocationDataRequest {
    
    @NotBlank
    private String userId;
    
    @NotEmpty
    private List<@Valid LocationPoint> points;
    
    @Data
    public static class LocationPoint {
        @NotNull
        private Double latitude;
        
        @NotNull
        private Double longitude;
        
        @NotNull
        private String timestamp; // ISO8601 format
        
        @NotNull
        private Double accuracyMeters;
        
        private String activity; // Optional
    }
}
