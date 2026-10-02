package com.dedicatedcode.reitti.controller.api.v2;

import com.dedicatedcode.reitti.controller.error.PageNotFoundException;
import com.dedicatedcode.reitti.model.memory.MemoryTrip;
import com.dedicatedcode.reitti.model.memory.MemoryVisit;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.MemoryTripJdbcService;
import com.dedicatedcode.reitti.repository.MemoryVisitJdbcService;
import com.dedicatedcode.reitti.service.MemoryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/v2/memories")
public class MemoryDataController {
    private final MemoryService memoryService;
    private final MemoryTripJdbcService memoryTripJdbcService;
    private final MemoryVisitJdbcService memoryVisitJdbcService;

    public MemoryDataController(MemoryService memoryService, MemoryTripJdbcService memoryTripJdbcService, MemoryVisitJdbcService memoryVisitJdbcService) {
        this.memoryService = memoryService;
        this.memoryTripJdbcService = memoryTripJdbcService;
        this.memoryVisitJdbcService = memoryVisitJdbcService;
    }

    @GetMapping("/trips/{memoryId}/{blockId}")
    public List<MemoryTrip> loadTrips(@AuthenticationPrincipal User user, @PathVariable Long memoryId, @PathVariable Long blockId, @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone) {
        requireAccessibleBlock(user, memoryId, blockId);
        return memoryTripJdbcService.findByMemoryBlockId(blockId);
    }

    @GetMapping("/visits/{memoryId}/{blockId}")
    public List<MemoryVisit> loadVisits(@AuthenticationPrincipal User user, @PathVariable Long memoryId, @PathVariable Long blockId, @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone) {
        requireAccessibleBlock(user, memoryId, blockId);
        return memoryVisitJdbcService.findByMemoryBlockId(blockId);
    }

    private void requireAccessibleBlock(User user, Long memoryId, Long blockId) {
        memoryService.getMemoryById(user, memoryId)
                .flatMap(memory -> memoryService.getBlockOfMemory(user, memory.getId(), blockId))
                .orElseThrow(() -> new PageNotFoundException("Memory block not found"));
    }
}
