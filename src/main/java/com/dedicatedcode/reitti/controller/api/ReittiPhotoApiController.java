package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.controller.error.PageNotFoundException;
import com.dedicatedcode.reitti.model.memory.Memory;
import com.dedicatedcode.reitti.model.security.MagicLinkResourceType;
import com.dedicatedcode.reitti.model.security.TokenUser;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.MemoryService;
import com.dedicatedcode.reitti.service.StorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.authentication.core.Token;

@RestController
@RequestMapping("/api/v1/photos/reitti")
public class ReittiPhotoApiController {

    private final StorageService storageService;
    private final MemoryService memoryService;

    public ReittiPhotoApiController(StorageService storageService, MemoryService memoryService) {
        this.storageService = storageService;
        this.memoryService = memoryService;
    }

    @GetMapping("/{filename}")
    public ResponseEntity<InputStreamResource> getPhoto(@PathVariable String filename, @AuthenticationPrincipal User user) {
        try {
            StorageService.StorageContent result = this.storageService.read("images/" + filename);
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
            Memory memory = this.memoryService.getMemoryById(user, Long.valueOf(memoryId)).orElseThrow(() -> new PageNotFoundException("Memory not found"));
            if (user instanceof TokenUser tokenUser) {
                if (!tokenUser.grantsAccessTo(MagicLinkResourceType.MEMORY, memory.getId())) {
                    throw new PageNotFoundException("Access denied");
                }
            }
            StorageService.StorageContent result = this.storageService.read("memories/" + memoryId + "/" + filename);
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
