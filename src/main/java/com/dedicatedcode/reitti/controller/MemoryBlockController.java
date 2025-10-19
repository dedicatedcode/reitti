package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.dto.PhotoResponse;
import com.dedicatedcode.reitti.model.memory.*;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.MemoryService;
import com.dedicatedcode.reitti.service.S3Storage;
import com.dedicatedcode.reitti.service.integration.ImmichIntegrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Controller
@RequestMapping("/memories/{memoryId}/blocks")
public class MemoryBlockController {

    private final MemoryService memoryService;
    private final ImmichIntegrationService immichIntegrationService;
    private final S3Storage s3Storage;

    public MemoryBlockController(MemoryService memoryService, ImmichIntegrationService immichIntegrationService, S3Storage s3Storage) {
        this.memoryService = memoryService;
        this.immichIntegrationService = immichIntegrationService;
        this.s3Storage = s3Storage;
    }

    @DeleteMapping("/{blockId}")
    public String deleteBlock(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @PathVariable Long blockId,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        
        memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        memoryService.deleteBlock(blockId);
        
        if (hxRequest != null) {
            return "memories/fragments :: empty";
        }
        
        return "redirect:/memories/" + memoryId;
    }

    @GetMapping("/{blockId}/edit")
    public String editBlockForm(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @PathVariable Long blockId,
            Model model) {
        
        memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        MemoryBlock block = memoryService.getBlockById(blockId)
                .orElseThrow(() -> new IllegalArgumentException("Block not found"));
        
        model.addAttribute("memoryId", memoryId);
        model.addAttribute("block", block);
        
        switch (block.getBlockType()) {
            case VISIT:
                memoryService.getVisitBlock(blockId).ifPresent(visit -> 
                    model.addAttribute("visitBlock", visit));
                break;
            case TRIP:
                memoryService.getTripBlock(blockId).ifPresent(trip -> 
                    model.addAttribute("tripBlock", trip));
                break;
            case TEXT:
                memoryService.getTextBlock(blockId).ifPresent(text -> 
                    model.addAttribute("textBlock", text));
                break;
            case IMAGE_GALLERY:
                List<MemoryBlockImageGallery> images = memoryService.getImagesForBlock(blockId);
                model.addAttribute("images", images);
                break;
        }
        
        return "memories/blocks/edit";
    }

    @PostMapping("/{blockId}/text")
    public String updateTextBlock(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @PathVariable Long blockId,
            @RequestParam String headline,
            @RequestParam String content) {
        
        memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        MemoryBlockText textBlock = memoryService.getTextBlock(blockId)
                .orElseThrow(() -> new IllegalArgumentException("Text block not found"));
        
        MemoryBlockText updated = textBlock.withHeadline(headline).withContent(content);
        memoryService.updateTextBlock(updated);
        
        return "redirect:/memories/" + memoryId;
    }

    @DeleteMapping("/{blockId}/images/{imageId}")
    public String deleteImageFromGallery(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @PathVariable Long blockId,
            @PathVariable Long imageId) {
        
        memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        memoryService.deleteImageFromGallery(imageId);
        return "redirect:/memories/" + memoryId;
    }

    @PostMapping("/reorder")
    public String reorderBlocks(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @RequestParam List<Long> blockIds) {
        
        memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        memoryService.reorderBlocks(memoryId, blockIds);
        return "redirect:/memories/" + memoryId;
    }

    @PostMapping("/text")
    public String createTextBlock(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @RequestParam(required = false) String headline,
            @RequestParam(required = false) String content) {
        
        memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        MemoryBlock block = memoryService.addBlock(memoryId, BlockType.TEXT);
        memoryService.addTextBlock(block.getId(), headline, content);
        
        return "redirect:/memories/" + memoryId;
    }

    @PostMapping("/visit")
    public String createVisitBlock(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @RequestParam Long visitId) {
        
        memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));


        MemoryBlock block = memoryService.addBlock(memoryId, BlockType.VISIT);
        memoryService.addVisitBlock(user, block.getId(), visitId);
        
        return "redirect:/memories/" + memoryId;
    }

    @PostMapping("/trip")
    public String createTripBlock(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @RequestParam Long tripId) {
        
        memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        MemoryBlock block = memoryService.addBlock(memoryId, BlockType.TRIP);
        memoryService.addTripBlock(user, block.getId(), tripId);
        
        return "redirect:/memories/" + memoryId;
    }

    @PostMapping("/image-gallery")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createImageGalleryBlock(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @RequestBody Map<String, Object> request) {

        memoryService.getMemoryById(user, memoryId).orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        @SuppressWarnings("unchecked")
        List<Map<String, String>> images = (List<Map<String, String>>) request.get("images");
        
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("No images provided");
        }
        
        MemoryBlock block = memoryService.addBlock(memoryId, BlockType.IMAGE_GALLERY);
        List<MemoryBlockImageGallery.GalleryImage> imageBlocks = new ArrayList<>();

        for (Map<String, String> image : images) {
            String type = image.get("type");
            if ("immich".equals(type)) {
                String assetId = image.get("assetId");
                // Load the image from Immich and store it in S3 like in the uploadImage method
                ImmichIntegration integration = immichIntegrationService.getImmichIntegration(user)
                        .orElseThrow(() -> new IllegalArgumentException("Immich integration not found"));
                if (!integration.isEnabled()) {
                    throw new IllegalArgumentException("Immich integration is not enabled");
                }
                String url = integration.getServerUrl() + "/api/asset/" + assetId + "/original";
                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                headers.set("Authorization", "Bearer " + integration.getApiToken());
                org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);
                org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
                org.springframework.http.ResponseEntity<byte[]> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, byte[].class);
                byte[] imageData = response.getBody();
                String contentType = response.getHeaders().getContentType() != null ? response.getHeaders().getContentType().toString() : "image/jpeg";
                long contentLength = imageData.length;
                String filename = UUID.randomUUID() + getExtensionFromContentType(contentType);
                s3Storage.store("memories/" + memoryId + "/" + filename, new java.io.ByteArrayInputStream(imageData), contentLength, contentType);
                String imageUrl = "/api/v1/photos/reitti/memories/" + memoryId + "/" + filename;
                imageBlocks.add(new MemoryBlockImageGallery.GalleryImage(imageUrl, null));
            } else if ("upload".equals(type)) {
                String url = image.get("url");
                String name = image.get("name");
                imageBlocks.add(new MemoryBlockImageGallery.GalleryImage(url, name));
            }
        }

        this.memoryService.addImageGalleryBlock(block.getId(), imageBlocks);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("blockId", block.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/upload-image")
    @ResponseBody
    public ResponseEntity<Map<String, String>> uploadImage(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @RequestParam("file") MultipartFile file) {
        
        memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String filename = UUID.randomUUID() + extension;

            s3Storage.store("memories/" + memoryId + "/" + filename, file.getInputStream(), file.getSize(), file.getContentType());

            String fileUrl = "/api/v1/photos/reitti/memories/" + memoryId + "/" + filename;
            
            Map<String, String> response = new HashMap<>();
            response.put("url", fileUrl);
            response.put("name", originalFilename);
            return ResponseEntity.ok(response);
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file", e);
        }
    }

    @GetMapping("/immich-photos")
    public String getImmichPhotos(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "UTC") String timezone,
            Model model) {
        
        Memory memory = memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        ZoneId zoneId = ZoneId.of(timezone);
        LocalDate startDate = memory.getStartDate().atZone(zoneId).toLocalDate();
        LocalDate endDate = memory.getEndDate().atZone(zoneId).toLocalDate();
        
        List<PhotoResponse> allPhotos = immichIntegrationService.searchPhotosForRange(user, startDate, endDate, timezone);
        
        int pageSize = 6;
        int totalPages = (int) Math.ceil((double) allPhotos.size() / pageSize);
        int startIndex = page * pageSize;
        int endIndex = Math.min(startIndex + pageSize, allPhotos.size());
        
        List<PhotoResponse> pagePhotos = allPhotos.subList(startIndex, endIndex);
        
        model.addAttribute("photos", pagePhotos);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        
        return "memories/fragments :: immich-photos-grid";
    }

    private String getExtensionFromContentType(String contentType) {
        if ("image/jpeg".equals(contentType)) {
            return ".jpg";
        } else if ("image/png".equals(contentType)) {
            return ".png";
        } else if ("image/gif".equals(contentType)) {
            return ".gif";
        } else if ("image/webp".equals(contentType)) {
            return ".webp";
        } else {
            return ".jpg"; // default
        }
    }
}
