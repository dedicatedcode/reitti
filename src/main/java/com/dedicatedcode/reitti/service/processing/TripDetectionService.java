package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.ProcessedVisitCreatedEvent;
import com.dedicatedcode.reitti.model.*;
import com.dedicatedcode.reitti.repository.ProcessedVisitRepository;
import com.dedicatedcode.reitti.repository.RawLocationPointRepository;
import com.dedicatedcode.reitti.repository.TripRepository;
import com.dedicatedcode.reitti.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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

    @RabbitListener(queues = RabbitMQConfig.DETECT_TRIP_QUEUE, concurrency = "1")
    public void visitCreated(ProcessedVisitCreatedEvent event) {
        User user = this.userRepository.findByUsername(event.getUsername()).orElseThrow();

        Optional<ProcessedVisit> createdVisit = this.processedVisitRepository.findByUserAndId(user, event.getVisitId());
        createdVisit.ifPresent(visit -> {
            //find visits in timerange
            Instant searchStart = visit.getStartTime().minus(1, ChronoUnit.DAYS);
            Instant searchEnd = visit.getEndTime().plus(1, ChronoUnit.DAYS);

            List<ProcessedVisit> visits = this.processedVisitRepository.findByUserAndTimeOverlap(user, searchStart, searchEnd);

            visits.sort(Comparator.comparing(ProcessedVisit::getStartTime));

            if (visits.size() < 2) {
                logger.info("Not enough visits to detect trips for user: {}", user.getUsername());
                return;
            }

            // Iterate through consecutive visits to detect trips
            for (int i = 0; i < visits.size() - 1; i++) {
                ProcessedVisit startVisit = visits.get(i);
                ProcessedVisit endVisit = visits.get(i + 1);

                // Create a trip between these two visits
                createTripBetweenVisits(user, startVisit, endVisit);
            }
        });
    }

    private void createTripBetweenVisits(User user, ProcessedVisit startVisit, ProcessedVisit endVisit) {
        // Trip starts when the first visit ends
        Instant tripStartTime = startVisit.getEndTime();

        // Trip ends when the second visit starts
        Instant tripEndTime = endVisit.getStartTime();

        if (this.processedVisitRepository.findById(startVisit.getId()).isEmpty() || this.processedVisitRepository.findById(endVisit.getId()).isEmpty()) {
            logger.debug("One of the following visits [{},{}] where already deleted. Will skip trip creation.", startVisit.getId(), endVisit.getId());
            return;
        }
        // If end time is before or equal to start time, this is not a valid trip
        if (tripEndTime.isBefore(tripStartTime) || tripEndTime.equals(tripStartTime)) {
            logger.warn("Invalid trip time range detected for user {}: {} to {}",
                    user.getUsername(), tripStartTime, tripEndTime);
            return;
        }

        // Check if a trip already exists with the same start and end times
        if (tripRepository.existsByUserAndStartTimeAndEndTime(user, tripStartTime, tripEndTime)) {
            logger.debug("Trip already exists for user {} from {} to {}",
                    user.getUsername(), tripStartTime, tripEndTime);
            return;
        }


        if (tripRepository.existsByUserAndStartPlaceAndEndPlaceAndStartTimeAndEndTime(user, startVisit.getPlace(), endVisit.getPlace(), tripStartTime, tripEndTime))  {
            logger.debug("Duplicated trip detected, will not store it");
            return;
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
        double estimatedDistanceInMeters = calculateDistanceBetweenPlaces(startVisit.getPlace(), endVisit.getPlace());
        trip.setEstimatedDistanceMeters(estimatedDistanceInMeters);

        // Calculate travelled distance (sum of distances between consecutive points)
        double travelledDistanceMeters = GeoUtils.calculateTripDistance(tripPoints);
        trip.setTravelledDistanceMeters(travelledDistanceMeters);

        // Infer transport mode based on speed and distance
        String transportMode = inferTransportMode(travelledDistanceMeters != 0 ? travelledDistanceMeters : estimatedDistanceInMeters, tripStartTime, tripEndTime);
        trip.setTransportModeInferred(transportMode);

        trip.setStartVisit(startVisit);
        trip.setEndVisit(endVisit);
        logger.debug("Created trip from {} to {}: travelled distance={}m, mode={}",
                startVisit.getPlace().getName(), endVisit.getPlace().getName(), Math.round(travelledDistanceMeters), transportMode);

        // Save and return the trip
        try {
            if (this.processedVisitRepository.existsById(trip.getStartVisit().getId()) &&  this.processedVisitRepository.existsById(trip.getEndVisit().getId())) {
                try {
                    tripRepository.save(trip);
                } catch (Exception e) {
                    logger.warn("Could not save trip.");
                }
            }
        } catch (Exception e) {
            logger.debug("Duplicated trip: [{}] detected. Will not store it.", trip);
        }
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
