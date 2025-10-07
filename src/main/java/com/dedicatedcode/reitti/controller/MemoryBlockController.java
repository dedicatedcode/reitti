package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.memory.*;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.MemoryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/memories/{memoryId}/blocks")
public class MemoryBlockController {

    private final MemoryService memoryService;

    public MemoryBlockController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @DeleteMapping("/{blockId}")
    public String deleteBlock(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @PathVariable Long blockId) {
        
        // Verify user owns the memory
        memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        memoryService.deleteBlock(blockId);
        return "redirect:/memories/" + memoryId;
    }

    @GetMapping("/{blockId}/edit")
    public String editBlockForm(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @PathVariable Long blockId,
            Model model) {
        
        // Verify user owns the memory
        memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        MemoryBlock block = memoryService.getBlockById(blockId)
                .orElseThrow(() -> new IllegalArgumentException("Block not found"));
        
        model.addAttribute("memoryId", memoryId);
        model.addAttribute("block", block);
        
        // Load block-specific data
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
        
        // Verify user owns the memory
        memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        MemoryBlockText textBlock = memoryService.getTextBlock(blockId)
                .orElseThrow(() -> new IllegalArgumentException("Text block not found"));
        
        MemoryBlockText updated = textBlock.withHeadline(headline).withContent(content);
        memoryService.updateTextBlock(updated);
        
        return "redirect:/memories/" + memoryId;
    }

    @PostMapping("/{blockId}/images")
    public String addImageToGallery(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @PathVariable Long blockId,
            @RequestParam String imageUrl,
            @RequestParam(required = false) String caption) {
        
        // Verify user owns the memory
        memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        memoryService.addImageToGallery(blockId, imageUrl, caption);
        return "redirect:/memories/" + memoryId;
    }

    @DeleteMapping("/{blockId}/images/{imageId}")
    public String deleteImageFromGallery(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @PathVariable Long blockId,
            @PathVariable Long imageId) {
        
        // Verify user owns the memory
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
        
        // Verify user owns the memory
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
        
        // Verify user owns the memory
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
        
        // Verify user owns the memory
        memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        MemoryBlock block = memoryService.addBlock(memoryId, BlockType.VISIT);
        memoryService.addVisitBlock(block.getId(), visitId);
        
        return "redirect:/memories/" + memoryId;
    }

    @PostMapping("/trip")
    public String createTripBlock(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @RequestParam Long tripId) {
        
        // Verify user owns the memory
        memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        MemoryBlock block = memoryService.addBlock(memoryId, BlockType.TRIP);
        memoryService.addTripBlock(block.getId(), tripId);
        
        return "redirect:/memories/" + memoryId;
    }

    @PostMapping("/image-gallery")
    public String createImageGalleryBlock(
            @AuthenticationPrincipal User user,
            @PathVariable Long memoryId,
            @RequestParam String imageUrl,
            @RequestParam(required = false) String caption) {
        
        // Verify user owns the memory
        memoryService.getMemoryById(user, memoryId)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        MemoryBlock block = memoryService.addBlock(memoryId, BlockType.IMAGE_GALLERY);
        memoryService.addImageToGallery(block.getId(), imageUrl, caption);
        
        return "redirect:/memories/" + memoryId;
    }
}
