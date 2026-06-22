package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.metadata.MemoryMetadata;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.MetadataOverrideJdbcService;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class MetadataOverrideService {

    private final MetadataOverrideJdbcService overrideJdbcService;
    private final TripJdbcService tripJdbcService;
    private final ProcessedVisitJdbcService processedVisitJdbcService;

    public MetadataOverrideService(MetadataOverrideJdbcService overrideJdbcService,
                                   TripJdbcService tripJdbcService,
                                   ProcessedVisitJdbcService processedVisitJdbcService) {
        this.overrideJdbcService = overrideJdbcService;
        this.tripJdbcService = tripJdbcService;
        this.processedVisitJdbcService = processedVisitJdbcService;
    }

    /**
     * Requirement 1: User saves metadata from the UI form.
     * Dual-writes to the active cached entity and the persistent vault table.
     */
    @Transactional
    public void saveTripMetadata(User user, Trip currentTrip, MemoryMetadata dto) {
        try {
            MemoryMetadata override = this.overrideJdbcService
                    .findBestOverlappingOverride(user, currentTrip.getStartTime(), currentTrip.getEndTime()).orElseGet(() -> {
                MemoryMetadata metadata = new MemoryMetadata(currentTrip.getStartTime(), currentTrip.getEndTime());
                this.overrideJdbcService.insertOverride(user, "TRIP", metadata);
                return metadata;
            });
            override.setProperties(dto.getProperties());
            this.overrideJdbcService.updateOverridePayload(user, override);
            this.tripJdbcService.update(currentTrip.withMetadata(override.getProperties()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize and save metadata", e);
        }
    }

    @Transactional
    public void saveVisitMetadata(User user, ProcessedVisit currentVisit, MemoryMetadata dto) {
        try {
            MemoryMetadata override = this.overrideJdbcService
                    .findBestOverlappingOverride(user, currentVisit.getStartTime(), currentVisit.getEndTime()).orElseGet(() -> {
                        MemoryMetadata metadata = new MemoryMetadata(currentVisit.getStartTime(), currentVisit.getEndTime());
                        this.overrideJdbcService.insertOverride(user, "VISIT", metadata);
                        return metadata;
                    });
            override.setProperties(dto.getProperties());
            this.overrideJdbcService.updateOverridePayload(user, override);
            this.processedVisitJdbcService.update(currentVisit.withMetadata(override.getProperties()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize and save metadata", e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<MemoryMetadata> findOverlappingMetadata(User user, Instant startTime, Instant endTime) {
        return overrideJdbcService
                .findBestOverlappingOverride(user, startTime, endTime);
    }

    public List<String> loadSuggestions(User user, String field, String query) {
        return overrideJdbcService.findDistinctSuggestions(user, field, query);
    }
}