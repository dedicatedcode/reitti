package com.dedicatedcode.reitti.controller.api.v2;

import com.dedicatedcode.reitti.model.memory.MemoryBlock;
import com.dedicatedcode.reitti.model.memory.MemoryTrip;
import com.dedicatedcode.reitti.model.memory.MemoryVisit;
import com.dedicatedcode.reitti.model.security.MagicLinkResourceType;
import com.dedicatedcode.reitti.model.security.TokenUser;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.MemoryBlockJdbcService;
import com.dedicatedcode.reitti.repository.MemoryTripJdbcService;
import com.dedicatedcode.reitti.repository.MemoryVisitJdbcService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/v2/memories")
public class MemoryDataController {
    private final MemoryTripJdbcService memoryTripJdbcService;
    private final MemoryVisitJdbcService memoryVisitJdbcService;
    private final MemoryBlockJdbcService memoryBlockJdbcService;

    public MemoryDataController(MemoryTripJdbcService memoryTripJdbcService, MemoryVisitJdbcService memoryVisitJdbcService, MemoryBlockJdbcService memoryBlockJdbcService) {
        this.memoryTripJdbcService = memoryTripJdbcService;
        this.memoryVisitJdbcService = memoryVisitJdbcService;
        this.memoryBlockJdbcService = memoryBlockJdbcService;
    }

    @GetMapping("/trips/{memoryId}/{blockId}")
    public List<MemoryTrip> loadTrips(@AuthenticationPrincipal User user, @PathVariable Long memoryId, @PathVariable Long blockId, @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone) {
        requireBlock(user, memoryId, blockId);
        return memoryTripJdbcService.findByMemoryBlockId(user, blockId);
    }

    @GetMapping("/visits/{memoryId}/{blockId}")
    public List<MemoryVisit> loadVisits(@AuthenticationPrincipal User user, @PathVariable Long memoryId, @PathVariable Long blockId, @RequestParam(required = false, defaultValue = "UTC") ZoneId timezone) {
        requireBlock(user, memoryId, blockId);
        return memoryVisitJdbcService.findByMemoryBlockId(user, blockId);
    }

    private MemoryBlock requireBlock(User user, Long memoryId, Long blockId) {
        MemoryBlock block = this.memoryBlockJdbcService.findById(user, blockId)
                .filter(candidate -> candidate.getMemoryId().equals(memoryId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (user instanceof TokenUser tokenUser
                && !tokenUser.grantsAccessTo(MagicLinkResourceType.MEMORY, memoryId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return block;
    }
}
