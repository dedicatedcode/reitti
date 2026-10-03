package com.dedicatedcode.reitti.controller.api.v2;

import com.dedicatedcode.reitti.model.metadata.MemoryMetadata;
import com.dedicatedcode.reitti.model.metadata.Mood;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import com.dedicatedcode.reitti.service.MetadataOverrideService;
import com.dedicatedcode.reitti.service.processing.TimeRange;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v2/metadata")
public class MetadataApiController {
    private final TripJdbcService tripJdbcService;
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final MetadataOverrideService metadataOverrideService;

    public MetadataApiController(TripJdbcService tripJdbcService,
                                 ProcessedVisitJdbcService processedVisitJdbcService,
                                 MetadataOverrideService metadataOverrideService) {
        this.tripJdbcService = tripJdbcService;
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.metadataOverrideService = metadataOverrideService;
    }

    @GetMapping("/{type}/{id}")
    public MemoryMetadata getMetadata(@AuthenticationPrincipal User user, @PathVariable String type, @PathVariable Long id) {
        TimeRange timeRange = findTimeRange(user, type, id);
        return this.metadataOverrideService.findOverlappingMetadata(user, timeRange.start(), timeRange.end()).orElse(null);
    }

    @PostMapping("/{type}/{id}")
    @Transactional
    public MemoryMetadata postMetadata(@AuthenticationPrincipal User user,
                                       @RequestParam(required = false) String mood,
                                       @RequestParam(required = false) String reason,
                                       @RequestParam(required = false) String notes,
                                       @RequestParam(required = false) List<String> tags,
                                       @PathVariable String type,
                                       @PathVariable Long id) {

        return switch (type) {
            case "trip" -> this.tripJdbcService.findByUserAndId(user, id).map(t -> {
                MemoryMetadata memoryMetadata = new MemoryMetadata(t.getStartTime(), t.getEndTime());
                memoryMetadata.setMood(mood != null ? Mood.valueOf(mood) : null);
                memoryMetadata.setReason(reason);
                memoryMetadata.setDescription(notes);
                memoryMetadata.setTags(tags);
                this.metadataOverrideService.saveTripMetadata(user, t, memoryMetadata);
                return memoryMetadata;
            }).orElseThrow(() -> notFound("Trip not found"));
            case "visit" -> this.processedVisitJdbcService.findByUserAndId(user, id).map(p -> {
                MemoryMetadata memoryMetadata = new MemoryMetadata(p.getStartTime(), p.getEndTime());
                memoryMetadata.setMood(mood != null ? Mood.valueOf(mood) : null);
                memoryMetadata.setReason(reason);
                memoryMetadata.setDescription(notes);
                memoryMetadata.setTags(tags);
                this.metadataOverrideService.saveVisitMetadata(user, p, memoryMetadata);
                return memoryMetadata;
            }).orElseThrow(() -> notFound("Visit not found"));
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    private TimeRange findTimeRange(User user, String type, Long id) {
        return switch (type) {
            case ("trip") ->
                    this.tripJdbcService.findByUserAndId(user, id).map(t -> TimeRange.of(t.getStartTime(), t.getEndTime())).orElseThrow(() -> notFound("Trip not found"));
            case ("visit") ->
                    this.processedVisitJdbcService.findByUserAndId(user, id).map(p -> TimeRange.of(p.getStartTime(), p.getEndTime())).orElseThrow(() -> notFound("Visit not found"));
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    private static ResponseStatusException notFound(String reason) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, reason);
    }
}
