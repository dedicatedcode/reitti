package com.dedicatedcode.reitti.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/preview")
public class PreviewApiController {
    
    @GetMapping("/{previewId}/status")
    public ResponseEntity<Map<String, Object>> getPreviewStatus(@PathVariable String previewId) {
        // TODO: Check actual preview processing status
        // For now, simulate that preview is ready after some time
        boolean ready = true; // This should check actual processing status
        
        return ResponseEntity.ok(Map.of(
            "ready", ready,
            "previewId", previewId
        ));
    }
}
