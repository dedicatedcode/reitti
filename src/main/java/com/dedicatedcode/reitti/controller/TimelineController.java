package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.dto.TimelineResponse;
import com.dedicatedcode.reitti.model.RawLocationPoint;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.RawLocationPointRepository;
import com.dedicatedcode.reitti.repository.UserRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class TimelineController {
    
    private final UserRepository userRepository;
    private final RawLocationPointRepository locationPointRepository;

    public TimelineController(UserRepository userRepository, RawLocationPointRepository locationPointRepository) {
        this.userRepository = userRepository;
        this.locationPointRepository = locationPointRepository;
    }

    @GetMapping("/timeline")
    public ResponseEntity<TimelineResponse> getTimeline(
            @RequestParam String userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        User user = userRepository.findByUsername(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        
        List<RawLocationPoint> points;
        if (date != null) {
            Instant dayStart = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
            points = locationPointRepository.findByUserAndDate(user, dayStart);
        } else {
            points = locationPointRepository.findByUserOrderByTimestampAsc(user);
        }
        
        // In a real implementation, we would return processed timeline entries (visits and trips)
        // For now, we'll return an empty response
        TimelineResponse response = new TimelineResponse(new ArrayList<>());
        
        return ResponseEntity.ok(response);
    }
}
