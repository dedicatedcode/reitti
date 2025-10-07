package com.dedicatedcode.reitti.controller;

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
            Model model) {
        Memory memory = memoryService.getMemoryById(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        List<MemoryBlock> blocks = memoryService.getBlocksForMemory(id);
        
        model.addAttribute("memory", memory);
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
            @RequestParam(required = false) String headerImageUrl) {
        
        Memory memory = new Memory(
                title,
                description,
                LocalDate.parse(startDate),
                LocalDate.parse(endDate),
                headerType,
                headerImageUrl
        );
        
        Memory created = memoryService.createMemory(user, memory);
        return "redirect:/memories/" + created.getId();
    }

    @GetMapping("/{id}/edit")
    public String editMemoryForm(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            Model model) {
        Memory memory = memoryService.getMemoryById(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        model.addAttribute("memory", memory);
        return "memories/edit";
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
            @RequestParam Long version) {
        
        Memory memory = memoryService.getMemoryById(user, id)
                .orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        
        Memory updated = memory
                .withTitle(title)
                .withDescription(description)
                .withStartDate(LocalDate.parse(startDate))
                .withEndDate(LocalDate.parse(endDate))
                .withHeaderType(headerType)
                .withHeaderImageUrl(headerImageUrl)
                .withVersion(version);
        
        memoryService.updateMemory(user, updated);
        return "redirect:/memories/" + id;
    }

    @DeleteMapping("/{id}")
    public String deleteMemory(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        memoryService.deleteMemory(user, id);
        return "redirect:/memories";
    }
}
