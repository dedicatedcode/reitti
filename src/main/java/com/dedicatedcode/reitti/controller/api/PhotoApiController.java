package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.dto.PhotoResponse;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.service.ImmichIntegrationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/photos")
public class PhotoApiController {
    
    private final ImmichIntegrationService immichIntegrationService;
    
    public PhotoApiController(ImmichIntegrationService immichIntegrationService) {
        this.immichIntegrationService = immichIntegrationService;
    }
    
    @GetMapping("/day/{date}")
    public ResponseEntity<List<PhotoResponse>> getPhotosForDay(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal User user) {
        
        List<PhotoResponse> photos = immichIntegrationService.searchPhotosForDay(user, date);
        return ResponseEntity.ok(photos);
    }
}
