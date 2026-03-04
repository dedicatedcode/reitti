package com.dedicatedcode.reitti.controller.api.v2;

import com.dedicatedcode.reitti.model.memory.MemoryTrip;
import com.dedicatedcode.reitti.model.memory.MemoryVisit;
import com.dedicatedcode.reitti.repository.MemoryTripJdbcService;
import com.dedicatedcode.reitti.repository.MemoryVisitJdbcService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v2/memmories")
public class MemoryDataController {
    private final MemoryTripJdbcService memoryTripJdbcService;
    private final MemoryVisitJdbcService memoryVisitJdbcService;

    public MemoryDataController(MemoryTripJdbcService memoryTripJdbcService, MemoryVisitJdbcService memoryVisitJdbcService) {
        this.memoryTripJdbcService = memoryTripJdbcService;
        this.memoryVisitJdbcService = memoryVisitJdbcService;
    }

    @RequestMapping("/trips/{memoryId}/{blockId}")
    public List<MemoryTrip> loadTrips(@AuthenticationPrincipal User user, @PathVariable Long memoryId, @PathVariable Long blockId) {
        return memoryTripJdbcService.findByMemoryBlockId(blockId);
    }

    @RequestMapping("/visits/{memoryId}/{blockId}")
    public List<MemoryVisit> loadVisits(@AuthenticationPrincipal User user, @PathVariable Long memoryId, @PathVariable Long blockId) {
        return memoryVisitJdbcService.findByMemoryBlockId(blockId);
    }
}
