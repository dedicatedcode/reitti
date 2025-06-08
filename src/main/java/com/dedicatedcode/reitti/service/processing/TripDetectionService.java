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
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class TripDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(TripDetectionService.class);

    private final ProcessedVisitRepository processedVisitRepository;
    private final RawLocationPointRepository rawLocationPointRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final RabbitTemplate rabbitTemplate;

    public TripDetectionService(ProcessedVisitRepository processedVisitRepository,
                                RawLocationPointRepository rawLocationPointRepository,
                                TripRepository tripRepository,
                                UserRepository userRepository,
                                RabbitTemplate rabbitTemplate) {
        this.processedVisitRepository = processedVisitRepository;
        this.rawLocationPointRepository = rawLocationPointRepository;
        this.tripRepository = tripRepository;
        this.userRepository = userRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.DETECT_TRIP_QUEUE, concurrency = "1-16")
    public void detectTripsForUser(MergeVisitEvent event) {
        Optional<User> user = userRepository.findByUsername(event.getUserName());
        if (user.isEmpty()) {
            logger.warn("User not found for userName: {}", event.getUserName());
            return;
        }
        logger.info("Detecting trips for user: {}", user.get().getUsername());
        // Get all processed visits for the user, sorted by start time
        List<ProcessedVisit> visits;
        if (event.getStartTime() == null || event.getEndTime() == null) {
            visits = processedVisitRepository.findByUser(user.get());
        } else {
            visits = processedVisitRepository.findByUserAndStartTimeBetweenOrderByStartTimeAsc(user.get(), Instant.ofEpochMilli(event.getStartTime()), Instant.ofEpochMilli(event.getEndTime()));
        }
        List<Trip> detectedTrips = findDetectedTrips(user.get(), visits);
        if (!detectedTrips.isEmpty()) {
            this.rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.MERGE_TRIP_ROUTING_KEY, event);
        }
    }

    private List<Trip> findDetectedTrips(User user, List<ProcessedVisit> visits) {
        visits.sort(Comparator.comparing(ProcessedVisit::getStartTime));

        if (visits.size() < 2) {
            logger.info("Not enough visits to detect trips for user: {}", user.getUsername());
            return Collections.emptyList();
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
            logger.warn("Invalid trip time range detected for user {}: {} to {}",
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

        // Calculate travelled distance (sum of distances between consecutive points)
        double travelledDistanceMeters = GeoUtils.calculateTripDistance(tripPoints);
        trip.setTravelledDistanceMeters(travelledDistanceMeters);

        // Infer transport mode based on speed and distance
        String transportMode = inferTransportMode(travelledDistanceMeters, tripStartTime, tripEndTime);
        trip.setTransportModeInferred(transportMode);

        logger.debug("Created trip from {} to {}: travelled distance={}m, mode={}",
                startVisit.getPlace().getName(), endVisit.getPlace().getName(), Math.round(travelledDistanceMeters), transportMode);

        // Save and return the trip
        try {
            tripRepository.save(trip);
        } catch (DataIntegrityViolationException e) {
            logger.warn("Duplicated trip: {} detected. Will not store it.", trip);
        }
        return trip;
    }

    private double calculateDistanceBetweenPlaces(SignificantPlace place1, SignificantPlace place2) {
        return GeoUtils.distanceInMeters(
                place1.getLatitudeCentroid(), place1.getLongitudeCentroid(),
                place2.getLatitudeCentroid(), place2.getLongitudeCentroid());
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
}
