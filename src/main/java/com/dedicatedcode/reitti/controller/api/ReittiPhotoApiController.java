package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.service.S3Storage;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/photos")
public class ReittiPhotoApiController {

    private final S3Storage s3Storage;

    public ReittiPhotoApiController(S3Storage s3Storage) {
        this.s3Storage = s3Storage;
    }

    @GetMapping("/uploaded/{memoryId}/{filename}")
    public ResponseEntity<InputStreamResource> getUploadedPhoto(
            @PathVariable Long memoryId,
            @PathVariable String filename) {
        
        String key = "memories/" + memoryId + "/" + filename;
        S3Storage.S3Object s3Object = s3Storage.read(key);
        
        HttpHeaders headers = new HttpHeaders();
        if (s3Object.getContentType() != null) {
            headers.setContentType(MediaType.parseMediaType(s3Object.getContentType()));
        }
        if (s3Object.getContentLength() != null) {
            headers.setContentLength(s3Object.getContentLength());
        }
        headers.setCacheControl("public, max-age=3600");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(s3Object.getInputStream()));
    }
}
