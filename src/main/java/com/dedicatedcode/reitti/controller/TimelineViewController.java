package com.dedicatedcode.reitti.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class TimelineViewController {

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
}
