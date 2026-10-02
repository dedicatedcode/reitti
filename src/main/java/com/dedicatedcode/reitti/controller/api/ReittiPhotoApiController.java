package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.MemoryService;
import com.dedicatedcode.reitti.service.StorageService;
import com.dedicatedcode.reitti.service.memory.ImageFormat;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/photos/reitti")
public class ReittiPhotoApiController {
    private static final Pattern SAFE_FILENAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]*$");

    private final StorageService storageService;
    private final MemoryService memoryService;

    public ReittiPhotoApiController(StorageService storageService, MemoryService memoryService) {
        this.storageService = storageService;
        this.memoryService = memoryService;
    }

    @GetMapping("/{filename}")
    public ResponseEntity<InputStreamResource> getPhoto(@PathVariable String filename, @AuthenticationPrincipal User user) {
        return serveImage("images", filename);
    }

    @GetMapping("/memories/{memoryId}/{filename}")
    public ResponseEntity<InputStreamResource> getPhotoForMemory(@PathVariable Long memoryId,
                                                                 @PathVariable String filename,
                                                                 @AuthenticationPrincipal User user) {
        if (memoryService.getMemoryById(user, memoryId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return serveImage("memories/" + memoryId, filename);
    }

    private ResponseEntity<InputStreamResource> serveImage(String directory, String filename) {
        Optional<ImageFormat> format = SAFE_FILENAME.matcher(filename).matches() ? ImageFormat.fromFilename(filename) : Optional.empty();
        if (format.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        try {
            StorageService.StorageContent result = this.storageService.read(directory + "/" + filename);
            HttpHeaders responseHeaders = new HttpHeaders();
            // The type comes from the extension allowlist, never from probing the file: these files are served from
            // our origin and must only ever be rendered as images.
            responseHeaders.setContentType(MediaType.valueOf(format.get().getMediaType()));
            responseHeaders.setContentLength(result.getContentLength());
            responseHeaders.setCacheControl("private, max-age=3600");
            responseHeaders.set("X-Content-Type-Options", "nosniff");
            responseHeaders.set("Content-Security-Policy", "sandbox; default-src 'none'");
            return ResponseEntity.ok()
                    .headers(responseHeaders)
                    .body(new InputStreamResource(result.getInputStream()));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
