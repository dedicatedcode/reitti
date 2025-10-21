package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.dto.PhotoResponse;
import com.dedicatedcode.reitti.model.integration.ImmichIntegration;
import com.dedicatedcode.reitti.model.memory.*;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.TripJdbcService;
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
    private final TripJdbcService tripJdbcService;
    private final S3Storage s3Storage;

    public MemoryBlockController(MemoryService memoryService, ImmichIntegrationService immichIntegrationService, TripJdbcService tripJdbcService, S3Storage s3Storage) {
        this.memoryService = memoryService;
        this.immichIntegrationService = immichIntegrationService;
        this.tripJdbcService = tripJdbcService;
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
            @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
            Model model) {

        Memory memory = memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));

        MemoryBlock block = memoryService.getBlockById(blockId)
                .orElseThrow(() -> new IllegalArgumentException("Block not found"));
        
        model.addAttribute("memoryId", memoryId);
        model.addAttribute("block", block);
        
        switch (block.getBlockType()) {
            case IMAGE_GALLERY:
                boolean immichEnabled = immichIntegrationService.getIntegrationForUser(user)
                        .map(ImmichIntegration::isEnabled)
                        .orElse(false);
                model.addAttribute("immichEnabled", immichEnabled);
                model.addAttribute("imageBlock", memoryService.getBlock(user, timezone, memoryId, blockId).orElseThrow(() -> new IllegalArgumentException("Block not found")) );
                return "memories/blocks/edit :: edit-image-gallery-block";
            case VISIT:
                memoryService.getVisitBlock(blockId).ifPresent(visit -> 
                    model.addAttribute("visitBlock", visit));
                break;
            case TEXT:
                memoryService.getBlock(user, timezone, memoryId, blockId).ifPresent(text ->
                    model.addAttribute("textBlock", text));
                return "memories/blocks/edit :: edit-text-block";
            case CLUSTER_TRIP:
                memoryService.getBlock(user, timezone, memoryId, blockId).ifPresent(b ->
                        model.addAttribute("clusterTripBlock", b));
                model.addAttribute("availableTrips", this.tripJdbcService.findByUserAndTimeOverlap(user, memory.getStartDate(), memory.getEndDate()));
                return "memories/blocks/edit :: edit-cluster-trip-block";
        }

        return "memories/blocks/edit";
    }

    @GetMapping("/{blockId}/view")
    public String viewBlock(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @PathVariable Long blockId,
            @RequestParam(required = false, defaultValue = "UTC" ) ZoneId timezone,
            Model model) {

        Memory memory = memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        model.addAttribute("memory", memory);
        model.addAttribute("blocks", List.of(this.memoryService.getBlock(user, timezone, memoryId, blockId).orElseThrow(() -> new IllegalArgumentException("Block not found"))));

        return "memories/view :: view-block";
    }

    @PostMapping("/{blockId}/text")
    public String updateTextBlock(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @PathVariable Long blockId,
            @RequestParam String headline,
            @RequestParam String content,
            @RequestParam(required = false, defaultValue = "UTC" ) ZoneId timezone,
            Model model) {

        Memory memory = memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        MemoryBlockText textBlock = memoryService.getTextBlock(blockId)
                .orElseThrow(() -> new IllegalArgumentException("Text block not found"));
        
        MemoryBlockText updated = textBlock.withHeadline(headline).withContent(content);
        memoryService.updateTextBlock(user, updated);
        
        model.addAttribute("memory", memory);
        model.addAttribute("blocks", List.of(updated));

        return "memories/view :: view-block";
    }

    @PostMapping("/{blockId}/cluster-trips")
    public String updateClusterTripBlock(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @PathVariable Long blockId,
            @RequestParam(name = "selectedTrips") List<Long> tripIds,
            @RequestParam(required = false) String title,
            @RequestParam(required = false, defaultValue = "UTC" ) ZoneId timezone,
            Model model) {

        Memory memory = memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));

        MemoryClusterBlock block = memoryService.getClusterBlock(user, blockId)
                .orElseThrow(() -> new IllegalArgumentException("Cluster block not found"));

        MemoryClusterBlock updated = block.withPartIds(tripIds).withTitle(title);
        memoryService.updateClusterBlock(user, updated);

        model.addAttribute("memory", memory);
        model.addAttribute("blocks", List.of(this.memoryService.getBlock(user, timezone, memoryId, blockId).orElseThrow(() -> new IllegalArgumentException("Block not found"))));

        return "memories/view :: view-block";
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


    @PostMapping("/trip-cluster")
    public String createTripBlock(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @RequestParam(name = "selectedTrips") List<Long> tripIds,
            @RequestParam(required = false) String title,
            @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone,
            Model model) {

        Memory memory = memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));


        MemoryBlock block = memoryService.addBlock(memoryId, BlockType.CLUSTER_TRIP);
        MemoryClusterBlock clusterBlock = new MemoryClusterBlock(block.getId(), tripIds, title, null, BlockType.CLUSTER_TRIP);
        memoryService.createClusterBlock(user, clusterBlock);
        model.addAttribute("memory", memory);
        model.addAttribute("blocks", List.of(this.memoryService.getBlock(user, timezone, memoryId, block.getId()).orElseThrow(() -> new IllegalArgumentException("Block not found"))));

        return "memories/view :: view-block";
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
                String filename = this.immichIntegrationService.downloadImage(user, assetId, "memories/" + memoryId);
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

}
