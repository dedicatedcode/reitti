package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.model.RawLocationPoint;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.RawLocationPointRepository;
import com.dedicatedcode.reitti.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LocationDataController {
    
    private final UserRepository userRepository;
    private final RawLocationPointRepository locationPointRepository;
    
    @PostMapping("/location-data")
    public ResponseEntity<String> receiveLocationData(@Valid @RequestBody LocationDataRequest request) {
        User user = userRepository.findByUsername(request.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        
        List<RawLocationPoint> points = new ArrayList<>();
        
        for (LocationDataRequest.LocationPoint pointDto : request.getPoints()) {
            RawLocationPoint point = new RawLocationPoint();
            point.setUser(user);
            point.setLatitude(pointDto.getLatitude());
            point.setLongitude(pointDto.getLongitude());
            point.setTimestamp(Instant.parse(pointDto.getTimestamp()));
            point.setAccuracyMeters(pointDto.getAccuracyMeters());
            point.setActivityProvided(pointDto.getActivity());
            
            points.add(point);
        }
        
        locationPointRepository.saveAll(points);
        
        // In a real implementation, we would queue this data for processing
        // to detect significant places, visits, and trips
        
        return ResponseEntity.accepted().body("Location data received for processing");
    }
}
