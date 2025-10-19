package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.S3Storage;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/photos/reitti")
public class ReittiPhotoApiController {

    private final S3Storage s3Storage;

    public ReittiPhotoApiController(S3Storage s3Storage) {
        this.s3Storage = s3Storage;
    }

    @GetMapping("/{filename}")
    public ResponseEntity<InputStreamResource> getPhoto(@PathVariable String filename, @AuthenticationPrincipal User user) {
        try {
            S3Storage.S3Object result = this.s3Storage.read("images/" + filename);
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.valueOf(result.getContentType()));
            responseHeaders.setContentLength(result.getContentLength());
            responseHeaders.setCacheControl("public, max-age=3600");
            return ResponseEntity.ok()
                    .headers(responseHeaders)
                    .body(new InputStreamResource(result.getInputStream()));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
    @GetMapping("/memories/{memoryId}/{filename}")
    public ResponseEntity<InputStreamResource> getPhotoForMemory(@PathVariable String memoryId,
                                                                 @PathVariable String filename,
                                                                 @AuthenticationPrincipal User user) {
        try {
            S3Storage.S3Object result = this.s3Storage.read("meories/" + memoryId + "/" + filename);
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.valueOf(result.getContentType()));
            responseHeaders.setContentLength(result.getContentLength());
            responseHeaders.setCacheControl("public, max-age=3600");
            return ResponseEntity.ok()
                    .headers(responseHeaders)
                    .body(new InputStreamResource(result.getInputStream()));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
