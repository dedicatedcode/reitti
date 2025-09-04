package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.event.ProcessedVisitCreatedEvent;
import com.dedicatedcode.reitti.model.*;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.UserNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class TripDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(TripDetectionService.class);

    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final TripJdbcService tripJdbcService;
    private final UserJdbcService userJdbcService;
    private final UserNotificationService userNotificationService;
    private final ConcurrentHashMap<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    public TripDetectionService(ProcessedVisitJdbcService processedVisitJdbcService,
                                RawLocationPointJdbcService rawLocationPointJdbcService,
                                TripJdbcService tripJdbcService,
                                UserJdbcService userJdbcService,
                                UserNotificationService userNotificationService) {
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.tripJdbcService = tripJdbcService;
        this.userJdbcService = userJdbcService;
        this.userNotificationService = userNotificationService;
    }

    public void visitCreated(ProcessedVisitCreatedEvent event) {
        String username = event.getUsername();
        ReentrantLock userLock = userLocks.computeIfAbsent(username, k -> new ReentrantLock());
        
        userLock.lock();
        try {
            User user = this.userJdbcService.findByUsername(username).orElseThrow();

            Optional<ProcessedVisit> createdVisit = this.processedVisitJdbcService.findByUserAndId(user, event.getVisitId());
            createdVisit.ifPresent(visit -> {
                //find visits in timerange
                Instant searchStart = visit.getStartTime().minus(1, ChronoUnit.DAYS);
                Instant searchEnd = visit.getEndTime().plus(1, ChronoUnit.DAYS);

                List<ProcessedVisit> visits = this.processedVisitJdbcService.findByUserAndTimeOverlap(user, searchStart, searchEnd);

                if (visits.size() < 2) {
                    logger.info("Not enough visits to detect trips for user: {}", user.getUsername());
                    return;
                }

                List<Trip> trips = new ArrayList<>();
                // Iterate through consecutive visits to detect trips
                for (int i = 0; i < visits.size() - 1; i++) {
                    ProcessedVisit startVisit = visits.get(i);
                    ProcessedVisit endVisit = visits.get(i + 1);

                    // Create a trip between these two visits
                    Trip trip = createTripBetweenVisits(user, startVisit, endVisit);
                    if (trip != null) {
                        trips.add(trip);
                    }
                }

                tripJdbcService.bulkInsert(user, trips);
                userNotificationService.newTrips(user, trips);
            });
        } finally {
            userLock.unlock();
        }
    }

    private Trip createTripBetweenVisits(User user, ProcessedVisit startVisit, ProcessedVisit endVisit) {
        // Trip starts when the first visit ends
        Instant tripStartTime = startVisit.getEndTime();

        // Trip ends when the second visit starts
        Instant tripEndTime = endVisit.getStartTime();

        if (this.processedVisitJdbcService.findById(startVisit.getId()).isEmpty() || this.processedVisitJdbcService.findById(endVisit.getId()).isEmpty()) {
            logger.debug("One of the following visits [{},{}] where already deleted. Will skip trip creation.", startVisit.getId(), endVisit.getId());
            return null;
        }
        // If end time is before or equal to start time, this is not a valid trip
        if (tripEndTime.isBefore(tripStartTime) || tripEndTime.equals(tripStartTime)) {
            logger.warn("Invalid trip time range detected for user {}: {} to {}",
                    user.getUsername(), tripStartTime, tripEndTime);
            return null;
        }

        // Check if a trip already exists with the same start and end times
        if (tripJdbcService.existsByUserAndStartTimeAndEndTime(user, tripStartTime, tripEndTime)) {
            logger.debug("Trip already exists for user {} from {} to {}",
                    user.getUsername(), tripStartTime, tripEndTime);
            return null;
        }

        // Get location points between the two visits
        List<RawLocationPoint> tripPoints = rawLocationPointJdbcService
                .findByUserAndTimestampBetweenOrderByTimestampAsc(
                        user, tripStartTime, tripEndTime);
        double estimatedDistanceInMeters = calculateDistanceBetweenPlaces(startVisit.getPlace(), endVisit.getPlace());
        double travelledDistanceMeters = GeoUtils.calculateTripDistance(tripPoints);
        // Create a new trip
        String transportMode = inferTransportMode(travelledDistanceMeters != 0 ? travelledDistanceMeters : estimatedDistanceInMeters, tripStartTime, tripEndTime);
        Trip trip = new Trip(
                tripStartTime,
                tripEndTime,
                tripEndTime.getEpochSecond() - tripStartTime.getEpochSecond(),
                estimatedDistanceInMeters,
                travelledDistanceMeters,
                transportMode,
                startVisit,
                endVisit
        );
        logger.debug("Created trip from {} to {}: travelled distance={}m, mode={}",
                startVisit.getPlace().getName(), endVisit.getPlace().getName(), Math.round(travelledDistanceMeters), transportMode);

        // Save and return the trip
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
