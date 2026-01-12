package com.dedicatedcode.reitti.dto;

import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.memory.MemoryVisit;

import java.time.LocalDateTime;
import java.time.ZoneId;

public record VisitDTO(Long id, String name, LocalDateTime startTime, LocalDateTime endTime, boolean selected, boolean memoryVisit) {
    public static VisitDTO create(ProcessedVisit visit, ZoneId timezone) {
        LocalDateTime startTime = visit.getStartTime().atZone(timezone).toLocalDateTime();
        LocalDateTime endTime = visit.getEndTime().atZone(timezone).toLocalDateTime();
        return new VisitDTO(visit.getId(), visit.getPlace().getName(), startTime, endTime, false, false);
    }
    public static VisitDTO create(MemoryVisit visit, ZoneId timezone) {
        LocalDateTime startTime = visit.getStartTime().atZone(timezone).toLocalDateTime();
        LocalDateTime endTime = visit.getEndTime().atZone(timezone).toLocalDateTime();
        return new VisitDTO(visit.getId(), visit.getName(), startTime, endTime, true, true);
    }
}
