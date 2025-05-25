package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.model.Location;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/timeline")
public class TimelineController {

    @GetMapping
    public ResponseEntity<List<Location>> getTimelineLocations() {
        // This is a placeholder implementation
        // In a real application, this would fetch data from a service/repository
        List<Location> locations = Arrays.asList(
            new Location(60.1699, 24.9384, LocalDateTime.now().minusDays(2), "Helsinki Central Station"),
            new Location(60.1675, 24.9414, LocalDateTime.now().minusDays(1), "Esplanade Park"),
            new Location(60.1733, 24.9489, LocalDateTime.now(), "Helsinki Cathedral")
        );
        
        return ResponseEntity.ok(locations);
    }
}
