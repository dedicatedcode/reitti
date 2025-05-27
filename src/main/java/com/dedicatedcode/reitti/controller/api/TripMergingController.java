package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.model.Trip;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.UserRepository;
import com.dedicatedcode.reitti.service.processing.TripMergingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/trips/merge")
public class TripMergingController {

    private final TripMergingService tripMergingService;
    private final UserRepository userRepository;

    @Autowired
    public TripMergingController(TripMergingService tripMergingService, UserRepository userRepository) {
        this.tripMergingService = tripMergingService;
        this.userRepository = userRepository;
    }

    @PostMapping("/all")
    public ResponseEntity<?> mergeAllTrips() {
        List<Trip> mergedTrips = tripMergingService.mergeDuplicateTripsForAllUsers();
        return ResponseEntity.ok(Map.of(
                "message", "Successfully merged duplicate trips",
                "mergedCount", mergedTrips.size()
        ));
    }

    @PostMapping("/user/{userId}")
    public ResponseEntity<?> mergeTripsForUser(@PathVariable Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        List<Trip> mergedTrips = tripMergingService.mergeDuplicateTripsForUser(userOpt.get());
        return ResponseEntity.ok(Map.of(
                "message", "Successfully merged duplicate trips for user",
                "userId", userId,
                "mergedCount", mergedTrips.size()
        ));
    }
}
