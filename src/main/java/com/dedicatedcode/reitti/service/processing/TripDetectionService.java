package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.RawLocationPoint;
import com.dedicatedcode.reitti.model.SignificantPlace;
import com.dedicatedcode.reitti.model.Trip;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.model.Visit;
import com.dedicatedcode.reitti.repository.RawLocationPointRepository;
import com.dedicatedcode.reitti.repository.TripRepository;
import com.dedicatedcode.reitti.repository.VisitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class TripDetectionService {
    private static final Logger logger = LoggerFactory.getLogger(TripDetectionService.class);
    
    // Minimum duration in seconds for a valid trip
    private static final long MIN_TRIP_DURATION_SECONDS = 60; // 1 minute
    
    // Maximum gap in seconds between consecutive location points to be considered part of the same trip
    private static final long MAX_POINT_GAP_SECONDS = 300; // 5 minutes
    
    private final TripRepository tripRepository;
    private final VisitRepository visitRepository;
    private final RawLocationPointRepository locationPointRepository;
    
    @Autowired
    public TripDetectionService(
            TripRepository tripRepository,
            VisitRepository visitRepository,
            RawLocationPointRepository locationPointRepository) {
        this.tripRepository = tripRepository;
        this.visitRepository = visitRepository;
        this.locationPointRepository = locationPointRepository;
    }
    
    /**
     * Detects trips between significant places for a user.
     * 
     * @param user The user for whom to detect trips
     * @param updatedPlaces The list of significant places that were recently updated
     * @return The number of new trips detected
     */
    @Transactional
    public int detectTrips(User user, List<SignificantPlace> updatedPlaces) {
        logger.info("Starting trip detection for user {}", user.getUsername());
        
        // Get all visits for the user, sorted by start time
        List<Visit> visits = visitRepository.findByUser(user).stream()
                .sorted(Comparator.comparing(Visit::getStartTime))
                .collect(Collectors.toList());
        
        if (visits.size() < 2) {
            logger.info("Not enough visits to detect trips for user {}", user.getUsername());
            return 0;
        }
        
        int newTripsCount = 0;
        
        // Iterate through consecutive visits to detect trips between them
        for (int i = 0; i < visits.size() - 1; i++) {
            Visit startVisit = visits.get(i);
            Visit endVisit = visits.get(i + 1);
            
            // Check if a trip already exists between these visits
            boolean tripExists = tripRepository.existsByUserAndStartTimeAndEndTime(
                    user, startVisit.getEndTime(), endVisit.getStartTime());
            
            if (tripExists) {
                continue;
            }
            
            // Calculate duration between visits
            long durationSeconds = Duration.between(startVisit.getEndTime(), endVisit.getStartTime()).getSeconds();
            
            // Skip if duration is too short
            if (durationSeconds < MIN_TRIP_DURATION_SECONDS) {
                continue;
            }
            
            // Get location points between the two visits
            List<RawLocationPoint> tripPoints = locationPointRepository.findByUserAndTimestampBetweenOrderByTimestampAsc(
                    user, startVisit.getEndTime(), endVisit.getStartTime());
            
            // Skip if not enough points to form a trip
            if (tripPoints.size() < 2) {
                continue;
            }
            
            // Check for gaps in the location data
            boolean hasLargeGaps = false;
            for (int j = 0; j < tripPoints.size() - 1; j++) {
                long gap = Duration.between(
                        tripPoints.get(j).getTimestamp(), 
                        tripPoints.get(j + 1).getTimestamp()
                ).getSeconds();
                
                if (gap > MAX_POINT_GAP_SECONDS) {
                    hasLargeGaps = true;
                    break;
                }
            }
            
            if (hasLargeGaps) {
                logger.debug("Skipping trip with large gaps in location data");
                continue;
            }
            
            // Create and save the trip
            Trip trip = new Trip();
            trip.setUser(user);
            trip.setStartPlace(startVisit.getPlace());
            trip.setEndPlace(endVisit.getPlace());
            trip.setStartTime(startVisit.getEndTime());
            trip.setEndTime(endVisit.getStartTime());
            
            // Calculate estimated distance (simplified straight-line distance for now)
            double distance = calculateTripDistance(tripPoints);
            trip.setEstimatedDistanceMeters(distance);
            
            // Infer transport mode based on average speed
            String transportMode = inferTransportMode(distance, durationSeconds);
            trip.setTransportModeInferred(transportMode);
            
            tripRepository.save(trip);
            newTripsCount++;
            
            logger.debug("Created new trip: {} to {}, distance: {}m, mode: {}", 
                    startVisit.getPlace().getName(), 
                    endVisit.getPlace().getName(),
                    Math.round(distance),
                    transportMode);
        }
        
        logger.info("Completed trip detection for user {}, detected {} new trips", 
                user.getUsername(), newTripsCount);
        
        return newTripsCount;
    }
    
    /**
     * Calculates the total distance of a trip based on its location points.
     * This is a simplified implementation using straight-line distances between consecutive points.
     * 
     * @param points The list of location points for the trip
     * @return The total distance in meters
     */
    private double calculateTripDistance(List<RawLocationPoint> points) {
        double totalDistance = 0.0;
        
        for (int i = 0; i < points.size() - 1; i++) {
            RawLocationPoint p1 = points.get(i);
            RawLocationPoint p2 = points.get(i + 1);
            
            totalDistance += haversineDistance(
                    p1.getLatitude(), p1.getLongitude(),
                    p2.getLatitude(), p2.getLongitude());
        }
        
        return totalDistance;
    }
    
    /**
     * Calculates the haversine distance between two points on Earth.
     * 
     * @param lat1 Latitude of point 1
     * @param lon1 Longitude of point 1
     * @param lat2 Latitude of point 2
     * @param lon2 Longitude of point 2
     * @return The distance in meters
     */
    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Earth radius in meters
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
    
    /**
     * Infers the transport mode based on average speed.
     * This is a simplified implementation and could be improved with more sophisticated algorithms.
     * 
     * @param distanceMeters The trip distance in meters
     * @param durationSeconds The trip duration in seconds
     * @return The inferred transport mode
     */
    private String inferTransportMode(double distanceMeters, long durationSeconds) {
        // Avoid division by zero
        if (durationSeconds == 0) {
            return "UNKNOWN";
        }
        
        // Calculate average speed in m/s
        double speedMps = distanceMeters / durationSeconds;
        
        // Convert to km/h for easier thresholds
        double speedKmh = speedMps * 3.6;
        
        // Simple thresholds for transport mode inference
        if (speedKmh < 7) {
            return "WALKING";
        } else if (speedKmh < 20) {
            return "CYCLING";
        } else if (speedKmh < 120) {
            return "DRIVING";
        } else {
            return "TRANSIT"; // High-speed transit like train
        }
    }
}
package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.ProcessedVisit;
import com.dedicatedcode.reitti.model.SignificantPlace;
import com.dedicatedcode.reitti.model.Trip;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitRepository;
import com.dedicatedcode.reitti.repository.RawLocationPointRepository;
import com.dedicatedcode.reitti.repository.TripRepository;
import com.dedicatedcode.reitti.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class TripDetectionService {
    
    private static final Logger logger = LoggerFactory.getLogger(TripDetectionService.class);
    
    private final ProcessedVisitRepository processedVisitRepository;
    private final RawLocationPointRepository rawLocationPointRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    
    public TripDetectionService(ProcessedVisitRepository processedVisitRepository,
                               RawLocationPointRepository rawLocationPointRepository,
                               TripRepository tripRepository,
                               UserRepository userRepository) {
        this.processedVisitRepository = processedVisitRepository;
        this.rawLocationPointRepository = rawLocationPointRepository;
        this.tripRepository = tripRepository;
        this.userRepository = userRepository;
    }
    
    @Transactional
    public List<Trip> detectTripsForUser(User user) {
        logger.info("Detecting trips for user: {}", user.getUsername());
        
        // Get all processed visits for the user, sorted by start time
        List<ProcessedVisit> visits = processedVisitRepository.findByUser(user);
        visits.sort(Comparator.comparing(ProcessedVisit::getStartTime));
        
        if (visits.size() < 2) {
            logger.info("Not enough visits to detect trips for user: {}", user.getUsername());
            return new ArrayList<>();
        }
        
        List<Trip> detectedTrips = new ArrayList<>();
        
        // Iterate through consecutive visits to detect trips
        for (int i = 0; i < visits.size() - 1; i++) {
            ProcessedVisit startVisit = visits.get(i);
            ProcessedVisit endVisit = visits.get(i + 1);
            
            // Create a trip between these two visits
            Trip trip = createTripBetweenVisits(user, startVisit, endVisit);
            if (trip != null) {
                detectedTrips.add(trip);
            }
        }
        
        logger.info("Detected {} trips for user: {}", detectedTrips.size(), user.getUsername());
        return detectedTrips;
    }
    
    @Transactional
    public List<Trip> detectTripsForAllUsers() {
        logger.info("Detecting trips for all users");
        
        List<User> allUsers = userRepository.findAll();
        List<Trip> allDetectedTrips = new ArrayList<>();
        
        for (User user : allUsers) {
            List<Trip> userTrips = detectTripsForUser(user);
            allDetectedTrips.addAll(userTrips);
        }
        
        logger.info("Completed trip detection for all users. Total detected trips: {}", 
                allDetectedTrips.size());
        
        return allDetectedTrips;
    }
    
    private Trip createTripBetweenVisits(User user, ProcessedVisit startVisit, ProcessedVisit endVisit) {
        // Trip starts when the first visit ends
        Instant tripStartTime = startVisit.getEndTime();
        
        // Trip ends when the second visit starts
        Instant tripEndTime = endVisit.getStartTime();
        
        // If end time is before or equal to start time, this is not a valid trip
        if (tripEndTime.isBefore(tripStartTime) || tripEndTime.equals(tripStartTime)) {
            logger.debug("Invalid trip time range detected for user {}: {} to {}", 
                    user.getUsername(), tripStartTime, tripEndTime);
            return null;
        }
        
        // Check if a trip already exists with the same start and end times
        if (tripRepository.existsByUserAndStartTimeAndEndTime(user, tripStartTime, tripEndTime)) {
            logger.debug("Trip already exists for user {} from {} to {}", 
                    user.getUsername(), tripStartTime, tripEndTime);
            return null;
        }
        
        // Create a new trip
        Trip trip = new Trip();
        trip.setUser(user);
        trip.setStartTime(tripStartTime);
        trip.setEndTime(tripEndTime);
        
        // Set start and end places
        trip.setStartPlace(startVisit.getPlace());
        trip.setEndPlace(endVisit.getPlace());
        
        // Calculate estimated distance (straight-line distance between places)
        double distanceMeters = calculateDistanceBetweenPlaces(
                startVisit.getPlace(), endVisit.getPlace());
        trip.setEstimatedDistanceMeters(distanceMeters);
        
        // Infer transport mode based on speed and distance
        String transportMode = inferTransportMode(distanceMeters, tripStartTime, tripEndTime);
        trip.setTransportModeInferred(transportMode);
        
        // Save and return the trip
        return tripRepository.save(trip);
    }
    
    private double calculateDistanceBetweenPlaces(SignificantPlace place1, SignificantPlace place2) {
        return calculateHaversineDistance(
                place1.getLatitudeCentroid(), place1.getLongitudeCentroid(),
                place2.getLatitudeCentroid(), place2.getLongitudeCentroid());
    }
    
    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        // Earth radius in meters
        final double R = 6371000;
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
    
    private String inferTransportMode(double distanceMeters, Instant startTime, Instant endTime) {
        // Calculate duration in seconds
        long durationSeconds = endTime.getEpochSecond() - startTime.getEpochSecond();
        
        // Avoid division by zero
        if (durationSeconds <= 0) {
            return "UNKNOWN";
        }
        
        // Calculate speed in meters per second
        double speedMps = distanceMeters / durationSeconds;
        
        // Convert to km/h for easier interpretation
        double speedKmh = speedMps * 3.6;
        
        // Simple transport mode inference based on average speed
        if (speedKmh < 7) {
            return "WALKING";
        } else if (speedKmh < 20) {
            return "CYCLING";
        } else if (speedKmh < 120) {
            return "DRIVING";
        } else {
            return "TRANSIT"; // High-speed transit like train
        }
    }
    
    @Transactional
    public void clearTrips(User user) {
        List<Trip> userTrips = tripRepository.findByUser(user);
        tripRepository.deleteAll(userTrips);
        logger.info("Cleared {} trips for user: {}", userTrips.size(), user.getUsername());
    }
    
    @Transactional
    public void clearAllTrips() {
        tripRepository.deleteAll();
        logger.info("Cleared all trips");
    }
}
