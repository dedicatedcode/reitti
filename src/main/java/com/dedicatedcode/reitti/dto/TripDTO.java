package com.dedicatedcode.reitti.dto;

import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.memory.MemoryTrip;

import java.time.LocalDateTime;
import java.time.ZoneId;

public record TripDTO(Long id, LocalDateTime startTime, LocalDateTime endTime, VisitDTO startVisit, VisitDTO endVisit, boolean memoryTrip) {
    public static TripDTO create(Trip trip, ZoneId timezone) {
        LocalDateTime startTime = trip.getStartTime().atZone(timezone).toLocalDateTime();
        LocalDateTime endTime = trip.getEndTime().atZone(timezone).toLocalDateTime();
        return new TripDTO(trip.getId(), startTime, endTime, VisitDTO.create(trip.getStartVisit(), timezone), VisitDTO.create(trip.getEndVisit(), timezone), false);
    }

    public static TripDTO create(MemoryTrip trip, ZoneId timezone) {
        LocalDateTime startTime = trip.getStartTime().atZone(timezone).toLocalDateTime();
        LocalDateTime endTime = trip.getEndTime().atZone(timezone).toLocalDateTime();
        return new TripDTO(trip.getId(), startTime, endTime, VisitDTO.create(trip.getStartVisit(), timezone), VisitDTO.create(trip.getEndVisit(), timezone), true);
    }
}
