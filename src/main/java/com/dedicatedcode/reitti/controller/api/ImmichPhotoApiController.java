package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.dto.PhotoResponse;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.integration.ImmichIntegrationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/photos/immich")
public class ImmichPhotoApiController {
    
    private final ImmichIntegrationService immichIntegrationService;

    public ImmichPhotoApiController(ImmichIntegrationService immichIntegrationService) {
        this.immichIntegrationService = immichIntegrationService;
    }

    @GetMapping("/range")
    public ResponseEntity<List<PhotoResponse>> getPhotosForRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "UTC") String timezone,
            @AuthenticationPrincipal User user) {
        
        List<PhotoResponse> photos = immichIntegrationService.searchPhotosForRange(user, startDate, endDate, timezone);
        return ResponseEntity.ok(photos);
    }
    
    @GetMapping("/proxy/{assetId}/thumbnail")
    public ResponseEntity<byte[]> getPhotoThumbnail(
            @PathVariable String assetId,
            @AuthenticationPrincipal User user) {
        
        return immichIntegrationService.proxyImageRequest(user, assetId, "thumbnail");
    }
    
    @GetMapping("/proxy/{assetId}/original")
    public ResponseEntity<byte[]> getPhotoOriginal(
            @PathVariable String assetId,
            @AuthenticationPrincipal User user) {
        
        return immichIntegrationService.proxyImageRequest(user, assetId, "fullsize");
    }

}
