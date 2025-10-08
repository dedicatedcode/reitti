package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.memory.Memory;
import com.dedicatedcode.reitti.model.memory.MemoryBlock;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

@Service
public class MemoryBlockGenerationService {
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final TripJdbcService tripJdbcService;

    public MemoryBlockGenerationService(ProcessedVisitJdbcService processedVisitJdbcService, TripJdbcService tripJdbcService) {
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.tripJdbcService = tripJdbcService;
    }

    public List<MemoryBlock> generate(User user, Memory memory, ZoneId timeZone) {
        Instant startDate = memory.getStartDate();
        Instant endDate = memory.getEndDate();

        List<ProcessedVisit> allTripsInRange = this.processedVisitJdbcService.findByUserAndTimeOverlap(user, startDate, endDate);
        // we want to find the accomodation for this memory, take the list of processed visits, and take the one which was the most visited between 22:00 and 06:00 in the user timezone. Rmeeber the times in Visits are UTC. AI!
        return null;
    }
}
