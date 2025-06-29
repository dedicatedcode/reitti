package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.ProcessedVisit;
import com.dedicatedcode.reitti.model.SignificantPlace;
import com.dedicatedcode.reitti.model.Trip;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class TimelineViewController {

    private final UserJdbcService userJdbcService;
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final TripJdbcService tripJdbcService;

    @Autowired
    public TimelineViewController(UserJdbcService userJdbcService,
                                  ProcessedVisitJdbcService processedVisitJdbcService,
                                  TripJdbcService tripJdbcService) {
        this.userJdbcService = userJdbcService;
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.tripJdbcService = tripJdbcService;
    }
    
    /**
     * Format an Instant to a time string (HH:MM)
     */
    private String formatTime(Instant instant) {
        if (instant == null) {
            return "N/A";
        }
        return instant.atZone(ZoneId.systemDefault())
                .toLocalTime()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }

    @GetMapping("/timeline")
    public String getTimeline(@RequestParam(required = false) LocalDate selectedDate, 
                             @RequestParam(required = false, defaultValue = "1") Long userId,
                             Model model) {
        // Default to today if no date is provided
        LocalDate date = selectedDate != null ? selectedDate : LocalDate.now();
        
        // Find the user
        User user = userJdbcService.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        
        // Convert LocalDate to start and end Instant for the selected date
        Instant startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusMillis(1);
        
        // Get processed visits and trips for the user and date range
        List<ProcessedVisit> processedVisits = processedVisitJdbcService.findByUserAndTimeOverlap(
                user, startOfDay, endOfDay);
        List<Trip> trips = tripJdbcService.findByUserAndTimeOverlap(
                user, startOfDay, endOfDay);
        
        // Convert to format expected by the frontend
        List<Map<String, Object>> timelineEntries = new ArrayList<>();
        
        // Add processed visits to timeline
        for (ProcessedVisit visit : processedVisits) {
            SignificantPlace place = visit.getPlace();
            if (place != null) {
                Map<String, Object> visitEntry = new HashMap<>();
                visitEntry.put("id", "visit-" + visit.getId());
                visitEntry.put("type", "place");
                visitEntry.put("description", place.getName() != null ? place.getName() : "Unknown Place");
                visitEntry.put("startTime", formatTime(visit.getStartTime()));
                visitEntry.put("endTime", formatTime(visit.getEndTime()));
                visitEntry.put("latitude", place.getLatitudeCentroid());
                visitEntry.put("longitude", place.getLongitudeCentroid());
                
                if (place.getCategory() != null) {
                    visitEntry.put("category", place.getCategory());
                }
                if (place.getAddress() != null) {
                    visitEntry.put("address", place.getAddress());
                }
                
                timelineEntries.add(visitEntry);
            }
        }
        
        // Add trips to timeline
        for (Trip trip : trips) {
            Map<String, Object> tripEntry = new HashMap<>();
            tripEntry.put("id", "trip-" + trip.getId());
            tripEntry.put("type", "trip");
            tripEntry.put("description", trip.getTransportModeInferred() != null ? 
                    "Trip by " + trip.getTransportModeInferred() : "Trip");
            tripEntry.put("startTime", formatTime(trip.getStartTime()));
            tripEntry.put("endTime", formatTime(trip.getEndTime()));
            
            // Get origin and destination coordinates if available
            if (trip.getStartVisit().getPlace() != null) {
                tripEntry.put("startLatitude", trip.getStartVisit().getPlace().getLatitudeCentroid());
                tripEntry.put("startLongitude", trip.getStartVisit().getPlace().getLongitudeCentroid());
            }
            
            if (trip.getEndVisit().getPlace() != null) {
                tripEntry.put("endLatitude", trip.getEndVisit().getPlace().getLatitudeCentroid());
                tripEntry.put("endLongitude", trip.getEndVisit().getPlace().getLongitudeCentroid());
            }
            
            if (trip.getTransportModeInferred() != null) {
                tripEntry.put("transportMode", trip.getTransportModeInferred().toLowerCase());
            }
            if (trip.getTravelledDistanceMeters() != null) {
                tripEntry.put("distanceMeters", trip.getTravelledDistanceMeters());
            } else if (trip.getEstimatedDistanceMeters() != null) {
                tripEntry.put("distanceMeters", trip.getEstimatedDistanceMeters());
            }
            
            timelineEntries.add(tripEntry);
        }
        
        // Sort timeline entries by start time
        timelineEntries.sort((e1, e2) -> {
            String time1 = (String) e1.get("startTime");
            String time2 = (String) e2.get("startTime");
            return time1.compareTo(time2);
        });
        
        model.addAttribute("date", date);
        model.addAttribute("timelineEntries", timelineEntries);
        
        return "fragments/timeline :: timeline";
    }
}
