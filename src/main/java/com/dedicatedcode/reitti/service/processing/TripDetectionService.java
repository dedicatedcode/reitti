package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.MergeVisitEvent;
import com.dedicatedcode.reitti.model.*;
import com.dedicatedcode.reitti.repository.ProcessedVisitRepository;
import com.dedicatedcode.reitti.repository.RawLocationPointRepository;
import com.dedicatedcode.reitti.repository.TripRepository;
import com.dedicatedcode.reitti.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
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
    @RabbitListener(queues = RabbitMQConfig.DETECT_TRIP_QUEUE, concurrency = "1-16")
    public void detectTripsForUser(MergeVisitEvent event) {
        User user = this.userRepository.findById(event.getUserId()).orElseThrow(() -> new IllegalArgumentException("Unknown user id: " + event.getUserId()));
        logger.info("Detecting trips for user: {}", user.getUsername());
        // Get all processed visits for the user, sorted by start time
        List<ProcessedVisit> visits;
        if (event.getStartTime() == null || event.getEndTime() == null) {
            visits = processedVisitRepository.findByUser(user);
        } else {
            visits = processedVisitRepository.findByUserAndStartTimeBetweenOrderByStartTimeAsc(user, Instant.ofEpochMilli(event.getStartTime()), Instant.ofEpochMilli(event.getEndTime()));
        }
        findDetectedTrips(user, visits);
    }

    private List<Trip> findDetectedTrips(User user, List<ProcessedVisit> visits) {
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
        
        // Get location points between the two visits
        List<RawLocationPoint> tripPoints = rawLocationPointRepository
                .findByUserAndTimestampBetweenOrderByTimestampAsc(
                        user, tripStartTime, tripEndTime);
        
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
        
        // Calculate travelled distance (sum of distances between consecutive points)
        double travelledDistanceMeters = calculateTripDistance(tripPoints);
        trip.setTravelledDistanceMeters(travelledDistanceMeters);
        
        // Infer transport mode based on speed and distance
        // Use travelled distance if available, otherwise use estimated distance
        double distanceForSpeed = travelledDistanceMeters > 0 ? travelledDistanceMeters : distanceMeters;
        String transportMode = inferTransportMode(distanceForSpeed, tripStartTime, tripEndTime);
        trip.setTransportModeInferred(transportMode);
        
        logger.debug("Created trip from {} to {}: estimated distance={}m, travelled distance={}m, mode={}", 
                startVisit.getPlace().getName(), endVisit.getPlace().getName(),
                Math.round(distanceMeters), Math.round(travelledDistanceMeters), transportMode);
        
        // Save and return the trip
        return tripRepository.save(trip);
    }
    
    private double calculateDistanceBetweenPlaces(SignificantPlace place1, SignificantPlace place2) {
        return GeoUtils.calculateHaversineDistance(
                place1.getLatitudeCentroid(), place1.getLongitudeCentroid(),
                place2.getLatitudeCentroid(), place2.getLongitudeCentroid());
    }
    
    private double calculateTripDistance(List<RawLocationPoint> points) {
        if (points.size() < 2) {
            return 0.0;
        }
        
        double totalDistance = 0.0;
        
        for (int i = 0; i < points.size() - 1; i++) {
            RawLocationPoint p1 = points.get(i);
            RawLocationPoint p2 = points.get(i + 1);
            
            totalDistance += GeoUtils.calculateHaversineDistance(p1, p2);
        }
        
        return totalDistance;
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
