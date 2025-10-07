package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.memory.BlockType;
import com.dedicatedcode.reitti.model.memory.HeaderType;
import com.dedicatedcode.reitti.model.memory.Memory;
import com.dedicatedcode.reitti.model.memory.MemoryBlock;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.MemoryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/memories")
public class MemoryController {

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @GetMapping
    public String listMemories(@AuthenticationPrincipal User user, Model model) {
        List<Memory> memories = memoryService.getMemoriesForUser(user);
        model.addAttribute("memories", memories);
        return "memories/list";
    }

    @GetMapping("/{id}")
    public String viewMemory(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            Model model,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        Memory memory = memoryService.getMemoryById(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        model.addAttribute("memory", memory);
        
        if (hxRequest != null) {
            // Return just the memory header fragment for htmx requests
            return "memories/view :: memory-header";
        }
        
        List<MemoryBlock> blocks = memoryService.getBlocksForMemory(id);
        model.addAttribute("blocks", blocks);
        return "memories/view";
    }

    @GetMapping("/new")
    public String newMemoryForm(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Model model) {
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        return "memories/new :: new-memory";
    }

    @PostMapping
    public String createMemory(
            @AuthenticationPrincipal User user,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam HeaderType headerType,
            @RequestParam(required = false) String headerImageUrl,
            Model model) {

        // Validate required fields
        if (title == null || title.trim().isEmpty()) {
            model.addAttribute("error", "memory.validation.title.required");
            model.addAttribute("title", title);
            model.addAttribute("description", description);
            model.addAttribute("startDate", startDate);
            model.addAttribute("endDate", endDate);
            model.addAttribute("headerType", headerType);
            model.addAttribute("headerImageUrl", headerImageUrl);
            return "memories/new :: new-memory";
        }
        
        if (startDate == null || startDate.trim().isEmpty()) {
            model.addAttribute("error", "memory.validation.start.date.required");
            model.addAttribute("title", title);
            model.addAttribute("description", description);
            model.addAttribute("startDate", startDate);
            model.addAttribute("endDate", endDate);
            model.addAttribute("headerType", headerType);
            model.addAttribute("headerImageUrl", headerImageUrl);
            return "memories/new :: new-memory";
        }
        
        if (endDate == null || endDate.trim().isEmpty()) {
            model.addAttribute("error", "memory.validation.end.date.required");
            model.addAttribute("title", title);
            model.addAttribute("description", description);
            model.addAttribute("startDate", startDate);
            model.addAttribute("endDate", endDate);
            model.addAttribute("headerType", headerType);
            model.addAttribute("headerImageUrl", headerImageUrl);
            return "memories/new :: new-memory";
        }
        
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            LocalDate today = LocalDate.now();
            
            // Validate dates are not in the future
            if (start.isAfter(today) || end.isAfter(today)) {
                model.addAttribute("error", "memory.validation.date.future");
                model.addAttribute("title", title);
                model.addAttribute("description", description);
                model.addAttribute("startDate", startDate);
                model.addAttribute("endDate", endDate);
                model.addAttribute("headerType", headerType);
                model.addAttribute("headerImageUrl", headerImageUrl);
                return "memories/new :: new-memory";
            }
            
            // Validate end date is not before start date
            if (end.isBefore(start)) {
                model.addAttribute("error", "memory.validation.end.date.before.start");
                model.addAttribute("title", title);
                model.addAttribute("description", description);
                model.addAttribute("startDate", startDate);
                model.addAttribute("endDate", endDate);
                model.addAttribute("headerType", headerType);
                model.addAttribute("headerImageUrl", headerImageUrl);
                return "memories/new :: new-memory";
            }
            
            Memory memory = new Memory(
                    title.trim(),
                    description != null ? description.trim() : null,
                    start,
                    end,
                    headerType,
                    headerImageUrl
            );
            
            Memory created = memoryService.createMemory(user, memory);
            return "redirect:/memories/" + created.getId();
            
        } catch (Exception e) {
            model.addAttribute("error", "memory.validation.start.date.required");
            model.addAttribute("title", title);
            model.addAttribute("description", description);
            model.addAttribute("startDate", startDate);
            model.addAttribute("endDate", endDate);
            model.addAttribute("headerType", headerType);
            model.addAttribute("headerImageUrl", headerImageUrl);
            return "memories/new :: new-memory";
        }
    }

    @GetMapping("/{id}/edit")
    public String editMemoryForm(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            Model model,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        Memory memory = memoryService.getMemoryById(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        model.addAttribute("memory", memory);
        
        // Set cancel endpoint and form target based on context
        if (hxRequest != null) {
            // If called via htmx from view page, cancel should return header fragment
            model.addAttribute("cancelEndpoint", "/memories/" + id);
            model.addAttribute("cancelTarget", ".memory-header");
            model.addAttribute("formTarget", ".memory-header");
        } else {
            // If called directly, cancel should go to list
            model.addAttribute("cancelEndpoint", "/memories");
            model.addAttribute("cancelTarget", ".settings-content-area");
            model.addAttribute("formTarget", ".settings-content-area");
        }
        
        return "memories/edit :: edit-memory";
    }

    @PostMapping("/{id}")
    public String updateMemory(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam HeaderType headerType,
            @RequestParam(required = false) String headerImageUrl,
            @RequestParam Long version,
            Model model,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        
        Memory memory = memoryService.getMemoryById(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        // Add validation similar to create method
        if (title == null || title.trim().isEmpty()) {
            model.addAttribute("error", "memory.validation.title.required");
            model.addAttribute("memory", memory);
            return "memories/edit :: edit-memory";
        }
        
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            LocalDate today = LocalDate.now();
            
            if (start.isAfter(today) || end.isAfter(today)) {
                model.addAttribute("error", "memory.validation.date.future");
                model.addAttribute("memory", memory);
                return "memories/edit :: edit-memory";
            }
            
            if (end.isBefore(start)) {
                model.addAttribute("error", "memory.validation.end.date.before.start");
                model.addAttribute("memory", memory);
                return "memories/edit :: edit-memory";
            }
            
            Memory updated = memory
                    .withTitle(title.trim())
                    .withDescription(description != null ? description.trim() : null)
                    .withStartDate(start)
                    .withEndDate(end)
                    .withHeaderType(headerType)
                    .withHeaderImageUrl(headerImageUrl)
                    .withVersion(version);
            
            Memory savedMemory = memoryService.updateMemory(user, updated);
            model.addAttribute("memory", savedMemory);
            
            if (hxRequest != null) {
                // Return the updated memory header for htmx requests
                return "memories/view :: memory-header";
            }
            
            return "redirect:/memories/" + id;
            
        } catch (Exception e) {
            model.addAttribute("error", "memory.validation.start.date.required");
            model.addAttribute("memory", memory);
            return "memories/edit :: edit-memory";
        }
    }

    @DeleteMapping("/{id}")
    public String deleteMemory(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        memoryService.deleteMemory(user, id);
        return "redirect:/memories";
    }

    @GetMapping("/{id}/blocks/select-type")
    public String selectBlockType(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            Model model) {
        model.addAttribute("memoryId", id);
        return "memories/fragments :: block-type-selection";
    }

    @GetMapping("/fragments/empty")
    public String emptyFragment() {
        return "memories/fragments :: empty";
    }

    @GetMapping("/{id}/blocks/new")
    public String newBlockForm(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestParam String type,
            Model model) {
        Memory memory = memoryService.getMemoryById(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        model.addAttribute("memoryId", id);
        model.addAttribute("blockType", type);
        
        return switch (type) {
            case "TEXT" -> "memories/fragments :: text-block-form";
            case "VISIT" -> "memories/fragments :: visit-block-form";
            case "TRIP" -> "memories/fragments :: trip-block-form";
            case "IMAGE_GALLERY" -> "memories/fragments :: image-gallery-block-form";
            default -> throw new IllegalArgumentException("Unknown block type: " + type);
        };
    }

}
