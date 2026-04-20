package com.dedicatedcode.reitti.dto;

import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.memory.MemoryVisit;

import java.time.LocalDateTime;
import java.time.ZoneId;

public record VisitDTO(Long id, String name, LocalDateTime startTime, LocalDateTime endTime, boolean selected, boolean memoryVisit) {
    public static VisitDTO create(ProcessedVisit visit, ZoneId timezone) {
        LocalDateTime startTime = visit.getStartTime().atZone(timezone).toLocalDateTime();
        LocalDateTime endTime = visit.getEndTime().atZone(timezone).toLocalDateTime();
        String name = visit.getPlace().getName();
        if (name == null || name.isBlank()) {
            if (visit.getPlace().getCity() != null && !visit.getPlace().getCity().isBlank()) {
                name = visit.getPlace().getCity();
            } else {
                name = String.format("%.4f, %.4f",
                                     visit.getPlace().getLatitudeCentroid(),
                                     visit.getPlace().getLongitudeCentroid());
            }
        }
        return new VisitDTO(visit.getId(), name, startTime, endTime, false, false);
    }
    public static VisitDTO create(MemoryVisit visit, ZoneId timezone) {
        LocalDateTime startTime = visit.getStartTime().atZone(timezone).toLocalDateTime();
        LocalDateTime endTime = visit.getEndTime().atZone(timezone).toLocalDateTime();
        return new VisitDTO(visit.getId(), visit.getName(), startTime, endTime, true, true);
    }
}
