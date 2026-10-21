package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.ImageType;
import com.dedicatedcode.reitti.service.ImageTypeDetector;
import com.dedicatedcode.reitti.service.StorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/photos/reitti")
public class ReittiPhotoApiController {

    private final StorageService storageService;
    private final ImageTypeDetector imageTypeDetector;

    public ReittiPhotoApiController(StorageService storageService, ImageTypeDetector imageTypeDetector) {
        this.storageService = storageService;
        this.imageTypeDetector = imageTypeDetector;
    }

    @GetMapping("/{filename}")
    public ResponseEntity<InputStreamResource> getPhoto(@PathVariable String filename, @AuthenticationPrincipal User user) {
        try {
            StorageService.StorageContent result = this.storageService.read("images/" + filename);
            return imageResponse(result);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/memories/{memoryId}/{filename}")
    public ResponseEntity<InputStreamResource> getPhotoForMemory(@PathVariable String memoryId,
                                                                 @PathVariable String filename,
                                                                 @AuthenticationPrincipal User user) {
        try {
            StorageService.StorageContent result = this.storageService.read("memories/" + memoryId + "/" + filename);
            return imageResponse(result);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    private ResponseEntity<InputStreamResource> imageResponse(StorageService.StorageContent result) throws IOException {
        Optional<ImageType> imageType = imageTypeDetector.detectImage(result.getContentType());
        if (imageType.isEmpty()) {
            result.getInputStream().close();
            return ResponseEntity.notFound().build();
        }

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.parseMediaType(imageType.get().contentType()));
        responseHeaders.setContentLength(result.getContentLength());
        responseHeaders.setContentDisposition(ContentDisposition.inline().build());
        responseHeaders.setCacheControl("public, max-age=3600");
        responseHeaders.set("X-Content-Type-Options", "nosniff");
        responseHeaders.set("Content-Security-Policy", "default-src 'none'; sandbox");
        return ResponseEntity.ok()
                .headers(responseHeaders)
                .body(new InputStreamResource(result.getInputStream()));
    }
}
