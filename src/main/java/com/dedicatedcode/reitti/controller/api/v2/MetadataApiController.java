package com.dedicatedcode.reitti.controller.api.v2;

import com.dedicatedcode.reitti.model.metadata.MemoryMetadata;
import com.dedicatedcode.reitti.model.metadata.Mood;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.MetadataOverrideJdbcService;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import com.dedicatedcode.reitti.service.processing.TimeRange;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v2/metadata")
public class MetadataApiController {
    private final TripJdbcService tripJdbcService;
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final MetadataOverrideJdbcService metadataOverrideJdbcService;

    public MetadataApiController(TripJdbcService tripJdbcService, ProcessedVisitJdbcService processedVisitJdbcService, MetadataOverrideJdbcService metadataOverrideJdbcService) {
        this.tripJdbcService = tripJdbcService;
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.metadataOverrideJdbcService = metadataOverrideJdbcService;
    }

    @GetMapping("/{type}/{id}")
    public MemoryMetadata getMetadata(@AuthenticationPrincipal User user, @PathVariable String type, @PathVariable Long id) {
        TimeRange timeRange = findTimeRange(type, id);
        return this.metadataOverrideJdbcService.findBestOverlappingOverride(timeRange.start(), timeRange.end()).orElse(null);
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

        MemoryMetadata metadata = switch (type) {
            case "trip" -> this.tripJdbcService.findById(id).map(t -> {
                MemoryMetadata memoryMetadata = new MemoryMetadata(t.getStartTime(), t.getEndTime());
                memoryMetadata.setMood(mood != null ? Mood.valueOf(mood) : null);
                memoryMetadata.setReason(reason);
                memoryMetadata.setDescription(notes);
                memoryMetadata.setTags(tags);
                this.tripJdbcService.update(t.withMetadata(memoryMetadata.getProperties()));
                return memoryMetadata;
            }).orElseThrow(() -> new IllegalArgumentException("Trip not found"));
            case "visit" -> this.processedVisitJdbcService.findById(id).map(p -> {
                MemoryMetadata memoryMetadata = new MemoryMetadata(p.getStartTime(), p.getEndTime());
                memoryMetadata.setMood(mood != null ? Mood.valueOf(mood) : null);
                memoryMetadata.setReason(reason);
                memoryMetadata.setDescription(notes);
                memoryMetadata.setTags(tags);
                this.processedVisitJdbcService.update(p.withMetadata(memoryMetadata.getProperties()));
                return memoryMetadata;
            }).orElseThrow(() -> new IllegalArgumentException("Visit not found"));
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };

        this.metadataOverrideJdbcService.insertOverride(type, metadata);
        return metadata;
    }

    private TimeRange findTimeRange(String type, Long id) {
        return switch (type) {
            case ("trip") ->
                    this.tripJdbcService.findById(id).map(t -> TimeRange.of(t.getStartTime(), t.getEndTime())).orElseThrow(() -> new IllegalArgumentException("Trip not found"));
            case ("visit") ->
                    this.processedVisitJdbcService.findById(id).map(p -> TimeRange.of(p.getStartTime(), p.getEndTime())).orElseThrow(() -> new IllegalArgumentException("Visit not found"));
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }
}
