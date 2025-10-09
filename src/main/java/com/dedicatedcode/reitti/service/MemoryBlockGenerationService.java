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

    public List<MemoryBlock> generate(User user, Memory memory) {
        Instant startDate = memory.getStartDate();
        Instant endDate = memory.getEndDate();

        List<ProcessedVisit> allVisitsInRange = this.processedVisitJdbcService.findByUserAndTimeOverlap(user, startDate, endDate);
        
        // Find accommodation by analyzing visits that occur during sleeping hours (22:00 - 06:00)
        ProcessedVisit accommodation = findAccommodation(allVisitsInRange);
        
        // TODO: Generate memory blocks based on visits and trips
        // For now, return empty list until block generation logic is implemented
        return List.of();
    }
    
    private ProcessedVisit findAccommodation(List<ProcessedVisit> visits) {
        var sleepingHoursDuration = new java.util.HashMap<Long, Long>();
        
        for (ProcessedVisit visit : visits) {
            long durationInSleepingHours = calculateSleepingHoursDuration(visit);
            if (durationInSleepingHours > 0) {
                sleepingHoursDuration.merge(visit.getPlace().getId(), durationInSleepingHours, Long::sum);
            }
        }
        
        return sleepingHoursDuration.entrySet().stream()
            .max(java.util.Map.Entry.comparingByValue())
            .flatMap(entry -> visits.stream()
                .filter(visit -> visit.getPlace().getId().equals(entry.getKey()))
                .findFirst())
            .orElse(null);
    }
    
    private long calculateSleepingHoursDuration(ProcessedVisit visit) {
        ZoneId timeZone = visit.getPlace().getTimezone();
        var startLocal = visit.getStartTime().atZone(timeZone);
        var endLocal = visit.getEndTime().atZone(timeZone);
        
        long totalSleepingDuration = 0;
        
        var currentDay = startLocal.toLocalDate();
        var lastDay = endLocal.toLocalDate();
        
        while (!currentDay.isAfter(lastDay)) {
            var sleepStart = currentDay.atTime(22, 0).atZone(timeZone);
            var sleepEnd = currentDay.plusDays(1).atTime(6, 0).atZone(timeZone);
            
            var overlapStart = sleepStart.isAfter(startLocal) ? sleepStart : startLocal;
            var overlapEnd = sleepEnd.isBefore(endLocal) ? sleepEnd : endLocal;
            
            if (overlapStart.isBefore(overlapEnd)) {
                totalSleepingDuration += java.time.Duration.between(overlapStart, overlapEnd).getSeconds();
            }
            
            currentDay = currentDay.plusDays(1);
        }
        
        return totalSleepingDuration;
    }
}
