package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.RawLocationPoint;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.RawLocationPointRepository;
import com.dedicatedcode.reitti.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class TimelineViewController {

    private final RawLocationPointRepository rawLocationPointRepository;
    private final UserRepository userRepository;

    @Autowired
    public TimelineViewController(RawLocationPointRepository rawLocationPointRepository, 
                                 UserRepository userRepository) {
        this.rawLocationPointRepository = rawLocationPointRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/timeline")
    public String getTimeline(@RequestParam(required = false) LocalDate selectedDate, Model model) {
        // Default to today if no date is provided
        LocalDate date = selectedDate != null ? selectedDate : LocalDate.now();
        
        // Mock data - in a real application, this would come from a service
        List<Map<String, Object>> timelineEntries = new ArrayList<>();
        
        // Example entry 1: A place visit
        timelineEntries.add(Map.of(
            "id", "entry1",
            "type", "place",
            "description", "Home",
            "startTime", "07:00",
            "endTime", "08:30",
            "latitude", 60.1699,
            "longitude", 24.9384
        ));
        
        // Example entry 2: A trip
        timelineEntries.add(Map.of(
            "id", "entry2",
            "type", "trip",
            "description", "Morning commute",
            "startTime", "08:30",
            "endTime", "09:00",
            "startLatitude", 60.1699,
            "startLongitude", 24.9384,
            "endLatitude", 60.1675,
            "endLongitude", 24.9209,
            "transportMode", "walking"
        ));
        
        // Example entry 3: Another place
        timelineEntries.add(Map.of(
            "id", "entry3",
            "type", "place",
            "description", "Office",
            "startTime", "09:00",
            "endTime", "17:00",
            "latitude", 60.1675,
            "longitude", 24.9209
        ));
        
        model.addAttribute("date", date);
        model.addAttribute("timelineEntries", timelineEntries);
        
        return "fragments/timeline :: timeline";
    }
    
    @GetMapping("/api/raw-location-points")
    @ResponseBody
    public List<Map<String, Object>> getRawLocationPoints(
            @RequestParam(required = false) LocalDate selectedDate,
            @RequestParam(required = false, defaultValue = "1") Long userId) {
        
        // Default to today if no date is provided
        LocalDate date = selectedDate != null ? selectedDate : LocalDate.now();
        
        // Find the user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        
        // Convert LocalDate to Instant at start of day in UTC
        Instant dateInstant = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        
        // Get raw location points for the user and date
        List<RawLocationPoint> locationPoints = rawLocationPointRepository.findByUserAndDate(user, dateInstant);
        
        // Convert to format expected by the frontend
        List<Map<String, Object>> rawPoints = new ArrayList<>();
        for (RawLocationPoint point : locationPoints) {
            Map<String, Object> pointMap = new HashMap<>();
            pointMap.put("id", point.getId());
            pointMap.put("latitude", point.getLatitude());
            pointMap.put("longitude", point.getLongitude());
            pointMap.put("timestamp", point.getTimestamp().toEpochMilli());
            pointMap.put("accuracyMeters", point.getAccuracyMeters());
            
            if (point.getActivityProvided() != null) {
                pointMap.put("activity", point.getActivityProvided());
            }
            
            rawPoints.add(pointMap);
        }
        
        return rawPoints;
    }
}
