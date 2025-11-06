package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class HomeDetectionService {


    public Optional<ProcessedVisit> findAccommodation(List<ProcessedVisit> visits, Instant memoryStart, Instant memoryEnd) {
        Map<Long, Long> sleepingHoursDuration = new HashMap<>();

        for (ProcessedVisit visit : visits) {
            long durationInSleepingHours = calculateSleepingHoursDuration(visit, memoryStart, memoryEnd);
            if (durationInSleepingHours > 0) {
                sleepingHoursDuration.merge(visit.getPlace().getId(), durationInSleepingHours, Long::sum);
            }
        }

        if (sleepingHoursDuration.isEmpty()) {
            return Optional.empty();
        }

        // Find the maximum duration
        long maxDuration = sleepingHoursDuration.values().stream().max(Long::compare).orElse(0L);

        // Collect all visits for places with the maximum duration
        List<ProcessedVisit> candidates = visits.stream()
                .filter(visit -> sleepingHoursDuration.getOrDefault(visit.getPlace().getId(), 0L) == maxDuration)
                .toList();

        // Among candidates, select the one with endTime closest to memoryEnd
        return candidates.stream()
                .min((v1, v2) -> {
                    long diff1 = Math.abs(Duration.between(v1.getEndTime(), memoryEnd).getSeconds());
                    long diff2 = Math.abs(Duration.between(v2.getEndTime(), memoryEnd).getSeconds());
                    return Long.compare(diff1, diff2);
                });
    }


    private long calculateSleepingHoursDuration(ProcessedVisit visit, Instant notBefore, Instant notAfter) {
        ZoneId timeZone = visit.getPlace().getTimezone();
        if (timeZone == null) {
            timeZone = ZoneId.systemDefault();
        }

        // Constrain visit times to memory boundaries
        Instant constrainedStart = visit.getStartTime().isBefore(notBefore) ? notBefore : visit.getStartTime();
        Instant constrainedEnd = visit.getEndTime().isAfter(notAfter) ? notAfter : visit.getEndTime();

        var startLocal = constrainedStart.atZone(timeZone);
        var endLocal = constrainedEnd.atZone(timeZone);

        long totalSleepingDuration = 0;

        var currentDay = startLocal.toLocalDate();
        var lastDay = endLocal.toLocalDate();

        while (!currentDay.isAfter(lastDay)) {
            var sleepStart = currentDay.atTime(22, 0).atZone(timeZone);
            var sleepEnd = currentDay.plusDays(1).atTime(6, 0).atZone(timeZone);

            var overlapStart = sleepStart.isAfter(startLocal) ? sleepStart : startLocal;
            var overlapEnd = sleepEnd.isBefore(endLocal) ? sleepEnd : endLocal;

            if (overlapStart.isBefore(overlapEnd)) {
                totalSleepingDuration += Duration.between(overlapStart, overlapEnd).getSeconds();
            }

            currentDay = currentDay.plusDays(1);
        }

        return totalSleepingDuration;
    }

}
