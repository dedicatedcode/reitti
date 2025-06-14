package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.dto.PhotoResponse;
import com.dedicatedcode.reitti.model.ImmichIntegration;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.service.ImmichIntegrationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/photos")
public class PhotoApiController {
    
    private final ImmichIntegrationService immichIntegrationService;
    private final RestTemplate restTemplate;
    
    public PhotoApiController(ImmichIntegrationService immichIntegrationService, RestTemplate restTemplate) {
        this.immichIntegrationService = immichIntegrationService;
        this.restTemplate = restTemplate;
    }
    
    @GetMapping("/day/{date}")
    public ResponseEntity<List<PhotoResponse>> getPhotosForDay(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal User user) {
        
        List<PhotoResponse> photos = immichIntegrationService.searchPhotosForDay(user, date);
        return ResponseEntity.ok(photos);
    }
    
    @GetMapping("/proxy/{assetId}/thumbnail")
    public ResponseEntity<byte[]> getPhotoThumbnail(
            @PathVariable String assetId,
            @AuthenticationPrincipal User user) {
        
        return proxyImageRequest(user, assetId, "thumbnail");
    }
    
    @GetMapping("/proxy/{assetId}/original")
    public ResponseEntity<byte[]> getPhotoOriginal(
            @PathVariable String assetId,
            @AuthenticationPrincipal User user) {
        
        return proxyImageRequest(user, assetId, "original");
    }
    
    private ResponseEntity<byte[]> proxyImageRequest(User user, String assetId, String imageType) {
        Optional<ImmichIntegration> integrationOpt = immichIntegrationService.getIntegrationForUser(user);
        
        if (integrationOpt.isEmpty() || !integrationOpt.get().isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        
        ImmichIntegration integration = integrationOpt.get();
        
        try {
            String baseUrl = integration.getServerUrl().endsWith("/") ? 
                integration.getServerUrl() : integration.getServerUrl() + "/";
            String imageUrl = baseUrl + "api/assets/" + assetId + "/" + imageType;
            
            HttpHeaders headers = new HttpHeaders();
            headers.add("x-api-key", integration.getApiToken());
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<byte[]> response = restTemplate.exchange(
                imageUrl,
                HttpMethod.GET,
                entity,
                byte[].class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                HttpHeaders responseHeaders = new HttpHeaders();
                
                // Copy content type from Immich response if available
                if (response.getHeaders().getContentType() != null) {
                    responseHeaders.setContentType(response.getHeaders().getContentType());
                } else {
                    // Default to JPEG for images
                    responseHeaders.setContentType(MediaType.IMAGE_JPEG);
                }
                
                // Set cache headers for better performance
                responseHeaders.setCacheControl("public, max-age=3600");
                
                return new ResponseEntity<>(response.getBody(), responseHeaders, HttpStatus.OK);
            }
            
        } catch (Exception e) {
            // Log error but don't expose details
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.notFound().build();
    }
}
