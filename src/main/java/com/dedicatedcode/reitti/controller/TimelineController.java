package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.ProcessedVisit;
import com.dedicatedcode.reitti.model.SignificantPlace;
import com.dedicatedcode.reitti.model.Trip;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.PlaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/timeline")
public class TimelineController {

    private final PlaceService placeService;
    private final UserJdbcService userJdbcService;
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final TripJdbcService tripJdbcService;

    @Autowired
    public TimelineController(PlaceService placeService,
                             UserJdbcService userJdbcService,
                             ProcessedVisitJdbcService processedVisitJdbcService,
                             TripJdbcService tripJdbcService) {
        this.placeService = placeService;
        this.userJdbcService = userJdbcService;
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.tripJdbcService = tripJdbcService;
    }

    @GetMapping("/content")
    public String getTimelineContent(@RequestParam String date, Principal principal, Model model) {
        LocalDate selectedDate = LocalDate.parse(date);
        
        // Find the user by username
        User user = userJdbcService.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        
        // Convert LocalDate to start and end Instant for the selected date
        Instant startOfDay = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endOfDay = selectedDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusMillis(1);
        
        // Get processed visits and trips for the user and date range
        List<ProcessedVisit> processedVisits = processedVisitJdbcService.findByUserAndTimeOverlap(
                user, startOfDay, endOfDay);
        List<Trip> trips = tripJdbcService.findByUserAndTimeOverlap(
                user, startOfDay, endOfDay);
        
        // Convert to timeline entries
        List<TimelineEntry> entries = buildTimelineEntries(processedVisits, trips);
        
        model.addAttribute("entries", entries);
        return "fragments/timeline :: timeline-content";
    }
    
    /**
     * Build timeline entries from processed visits and trips
     */
    private List<TimelineEntry> buildTimelineEntries(List<ProcessedVisit> processedVisits, List<Trip> trips) {
        List<TimelineEntry> entries = new ArrayList<>();
        
        // Add processed visits to timeline
        for (ProcessedVisit visit : processedVisits) {
            SignificantPlace place = visit.getPlace();
            if (place != null) {
                TimelineEntry entry = new TimelineEntry();
                entry.setId("visit-" + visit.getId());
                entry.setType(TimelineEntry.Type.VISIT);
                entry.setPlace(place);
                entry.setStartTime(visit.getStartTime());
                entry.setEndTime(visit.getEndTime());
                entry.setFormattedTimeRange(formatTimeRange(visit.getStartTime(), visit.getEndTime()));
                entry.setFormattedDuration(formatDuration(visit.getStartTime(), visit.getEndTime()));
                entries.add(entry);
            }
        }
        
        // Add trips to timeline
        for (Trip trip : trips) {
            TimelineEntry entry = new TimelineEntry();
            entry.setId("trip-" + trip.getId());
            entry.setType(TimelineEntry.Type.TRIP);
            entry.setStartTime(trip.getStartTime());
            entry.setEndTime(trip.getEndTime());
            entry.setFormattedTimeRange(formatTimeRange(trip.getStartTime(), trip.getEndTime()));
            entry.setFormattedDuration(formatDuration(trip.getStartTime(), trip.getEndTime()));
            
            if (trip.getTravelledDistanceMeters() != null) {
                entry.setDistanceMeters(trip.getTravelledDistanceMeters());
            } else if (trip.getEstimatedDistanceMeters() != null) {
                entry.setDistanceMeters(trip.getEstimatedDistanceMeters());
            }
            
            if (trip.getTransportModeInferred() != null) {
                entry.setTransportMode(trip.getTransportModeInferred());
            }
            
            entries.add(entry);
        }
        
        // Sort timeline entries by start time
        entries.sort((e1, e2) -> e1.getStartTime().compareTo(e2.getStartTime()));
        
        return entries;
    }
    
    /**
     * Format time range for display
     */
    private String formatTimeRange(Instant startTime, Instant endTime) {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        String start = startTime.atZone(ZoneId.systemDefault()).format(timeFormatter);
        String end = endTime.atZone(ZoneId.systemDefault()).format(timeFormatter);
        return start + " - " + end;
    }
    
    /**
     * Format duration for display (this is a simple implementation, you might want to use HumanizeDuration)
     */
    private String formatDuration(Instant startTime, Instant endTime) {
        long durationMinutes = java.time.Duration.between(startTime, endTime).toMinutes();
        long hours = durationMinutes / 60;
        long minutes = durationMinutes % 60;
        
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + "m";
        }
    }
    
    /**
     * Inner class to represent timeline entries for the template
     */
    public static class TimelineEntry {
        public enum Type { VISIT, TRIP }
        
        private String id;
        private Type type;
        private SignificantPlace place;
        private Instant startTime;
        private Instant endTime;
        private String formattedTimeRange;
        private String formattedDuration;
        private Double distanceMeters;
        private String transportMode;
        
        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public Type getType() { return type; }
        public void setType(Type type) { this.type = type; }
        
        public SignificantPlace getPlace() { return place; }
        public void setPlace(SignificantPlace place) { this.place = place; }
        
        public Instant getStartTime() { return startTime; }
        public void setStartTime(Instant startTime) { this.startTime = startTime; }
        
        public Instant getEndTime() { return endTime; }
        public void setEndTime(Instant endTime) { this.endTime = endTime; }
        
        public String getFormattedTimeRange() { return formattedTimeRange; }
        public void setFormattedTimeRange(String formattedTimeRange) { this.formattedTimeRange = formattedTimeRange; }
        
        public String getFormattedDuration() { return formattedDuration; }
        public void setFormattedDuration(String formattedDuration) { this.formattedDuration = formattedDuration; }
        
        public Double getDistanceMeters() { return distanceMeters; }
        public void setDistanceMeters(Double distanceMeters) { this.distanceMeters = distanceMeters; }
        
        public String getTransportMode() { return transportMode; }
        public void setTransportMode(String transportMode) { this.transportMode = transportMode; }
    }

    @GetMapping("/places/edit-form/{id}")
    public String getPlaceEditForm(@PathVariable Long id, Model model) {
        SignificantPlace place = placeService.findById(id);
        model.addAttribute("place", place);
        return "fragments/place-edit :: edit-form";
    }

    @PutMapping("/places/{id}")
    public String updatePlace(@PathVariable Long id, @RequestParam String name, Model model) {
        SignificantPlace updated = placeService.updateName(id, name);
        model.addAttribute("place", updated);
        return "fragments/place-edit :: view-mode";
    }

    @GetMapping("/places/view/{id}")
    public String getPlaceView(@PathVariable Long id, Model model) {
        SignificantPlace place = placeService.findById(id);
        model.addAttribute("place", place);
        return "fragments/place-edit :: view-mode";
    }
}
