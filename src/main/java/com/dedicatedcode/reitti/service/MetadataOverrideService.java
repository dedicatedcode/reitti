package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.geo.Visit;
import com.dedicatedcode.reitti.model.metadata.MemoryMetadata;
import com.dedicatedcode.reitti.repository.MetadataOverrideJdbcService;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
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
    public void saveTripMetadata(Trip currentTrip, MemoryMetadata dto) {
        try {
            MemoryMetadata override = this.overrideJdbcService
                    .findBestOverlappingOverride(currentTrip.getStartTime(), currentTrip.getEndTime()).orElseGet(() -> {
                MemoryMetadata metadata = new MemoryMetadata(currentTrip.getStartTime(), currentTrip.getEndTime());
                this.overrideJdbcService.insertOverride("TRIP", metadata);
                return metadata;
            });
            override.setProperties(dto.getProperties());
            this.overrideJdbcService.updateOverridePayload(override);
            this.tripJdbcService.update(currentTrip.withMetadata(override.getProperties()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize and save metadata", e);
        }
    }

    @Transactional
    public void saveVisitMetadata(ProcessedVisit currentVisit, MemoryMetadata dto) {
        try {
            MemoryMetadata override = this.overrideJdbcService
                    .findBestOverlappingOverride(currentVisit.getStartTime(), currentVisit.getEndTime()).orElseGet(() -> {
                        MemoryMetadata metadata = new MemoryMetadata(currentVisit.getStartTime(), currentVisit.getEndTime());
                        this.overrideJdbcService.insertOverride("VISIT", metadata);
                        return metadata;
                    });
            override.setProperties(dto.getProperties());
            this.overrideJdbcService.updateOverridePayload(override);
            this.processedVisitJdbcService.update(currentVisit.withMetadata(override.getProperties()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize and save metadata", e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<MemoryMetadata> findOverlappingMetadata(Instant startTime, Instant endTime) {
        return overrideJdbcService
                .findBestOverlappingOverride(startTime, endTime);
    }
}