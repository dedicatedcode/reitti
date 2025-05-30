package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.config.RabbitMQConfig;
import com.dedicatedcode.reitti.event.MergeVisitEvent;
import com.dedicatedcode.reitti.model.GeoUtils;
import com.dedicatedcode.reitti.model.RawLocationPoint;
import com.dedicatedcode.reitti.model.Trip;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.RawLocationPointRepository;
import com.dedicatedcode.reitti.repository.TripRepository;
import com.dedicatedcode.reitti.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class TripMergingService {

    private static final Logger logger = LoggerFactory.getLogger(TripMergingService.class);

    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final RawLocationPointRepository rawLocationPointRepository;

    @Autowired
    public TripMergingService(TripRepository tripRepository,
                              UserRepository userRepository,
                              RawLocationPointRepository rawLocationPointRepository) {
        this.tripRepository = tripRepository;
        this.userRepository = userRepository;
        this.rawLocationPointRepository = rawLocationPointRepository;
    }

    @Transactional
    @RabbitListener(queues = RabbitMQConfig.MERGE_TRIP_QUEUE)
    public void mergeDuplicateTripsForUser(MergeVisitEvent event) {
        Optional<User> user = userRepository.findByUsername(event.getUserName());
        if (user.isEmpty()) {
            logger.warn("User not found for userName: {}", event.getUserName());
            return;
        }
        logger.info("Merging duplicate trips for user: {}", user.get().getUsername());

        // Get all trips for the user
        List<Trip> allTrips;
        if (event.getStartTime() == null || event.getEndTime() == null) {
            allTrips = tripRepository.findByUser(user.orElse(null));
        } else {
            allTrips = tripRepository.findByUserAndStartTimeBetweenOrderByStartTimeAsc(user.orElse(null), Instant.ofEpochMilli(event.getStartTime()), Instant.ofEpochMilli(event.getEndTime()));
        }

        mergeTrips(user.orElse(null), allTrips, true);
        mergeTrips(user.orElse(null), allTrips, false);

    }

    private void mergeTrips(User user, List<Trip> allTrips, boolean withStart) {

        if (allTrips.isEmpty()) {
            logger.info("No trips found for user: {}", user.getUsername());
            return;
        }

        // Group trips by start place, end place, and similar time range
        Map<String, List<Trip>> tripGroups = groupSimilarTrips(allTrips, withStart);

        // Process each group to merge duplicates
        List<Trip> tripsToDelete = new ArrayList<>();

        for (List<Trip> tripGroup : tripGroups.values()) {
            if (tripGroup.size() > 1) {
                mergeTrips(tripGroup, user);
                tripsToDelete.addAll(tripGroup);
            }
        }

        // Delete the original trips that were merged
        if (!tripsToDelete.isEmpty()) {
            tripRepository.deleteAll(tripsToDelete);
            logger.info("Deleted {} duplicate trips for user: {}", tripsToDelete.size(), user.getUsername());
        }
    }

    private Map<String, List<Trip>> groupSimilarTrips(List<Trip> trips, boolean withStart) {
        Map<String, List<Trip>> tripGroups = new HashMap<>();

        for (Trip trip : trips) {
            // Create a key based on start place, end place, and approximate time
            // We use minute precision for time to allow for small differences
            String key = createTripGroupKey(trip, withStart);
            tripGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(trip);
        }
        return tripGroups;
    }

    private String createTripGroupKey(Trip trip, boolean withStart) {
        // Create a key that identifies similar trips
        // Format: userId_startPlaceId_endPlaceId_startTimeMinute_endTimeMinute
        long timeKey = withStart ? trip.getStartTime().getEpochSecond() / 60 : trip.getEndTime().getEpochSecond() / 60;

        Long startPlaceId = trip.getStartPlace() != null ? trip.getStartPlace().getId() : 0;
        Long endPlaceId = trip.getEndPlace() != null ? trip.getEndPlace().getId() : 0;

        return String.format("%d_%d_%d_%d",
                trip.getUser().getId(),
                startPlaceId,
                endPlaceId,
                timeKey);
    }

    private Trip mergeTrips(List<Trip> trips, User user) {
        // Use the first trip as a base
        Trip baseTrip = trips.get(0);

        // Find the earliest start time and latest end time
        Instant earliestStart = baseTrip.getStartTime();
        Instant latestEnd = baseTrip.getEndTime();

        for (Trip trip : trips) {
            if (trip.getStartTime().isBefore(earliestStart)) {
                earliestStart = trip.getStartTime();
            }
            if (trip.getEndTime().isAfter(latestEnd)) {
                latestEnd = trip.getEndTime();
            }
        }

        // Create a new merged trip
        Trip mergedTrip = new Trip();
        mergedTrip.setUser(user);
        mergedTrip.setStartPlace(baseTrip.getStartPlace());
        mergedTrip.setEndPlace(baseTrip.getEndPlace());
        mergedTrip.setStartTime(earliestStart);
        mergedTrip.setEndTime(latestEnd);

        // Recalculate distance based on raw location points
        recalculateDistance(mergedTrip);

        // Set transport mode (use the most common one from the trips)
        mergedTrip.setTransportModeInferred(getMostCommonTransportMode(trips));

        // Save the merged trip
        return tripRepository.save(mergedTrip);
    }

    private void recalculateDistance(Trip trip) {
        // Get all raw location points for this user within the trip's time range
        List<RawLocationPoint> points = rawLocationPointRepository.findByUserAndTimestampBetweenOrderByTimestampAsc(
                trip.getUser(), trip.getStartTime(), trip.getEndTime());

        if (points.size() < 2) {
            // Not enough points to calculate distance
            trip.setTravelledDistanceMeters(0.0);
            return;
        }

        // Calculate total distance
        double totalDistance = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            RawLocationPoint p1 = points.get(i);
            RawLocationPoint p2 = points.get(i + 1);

            double distance = GeoUtils.calculateHaversineDistance(
                    p1.getLatitude(), p1.getLongitude(),
                    p2.getLatitude(), p2.getLongitude());

            totalDistance += distance;
        }

        trip.setTravelledDistanceMeters(totalDistance);

        // Also update the estimated distance
        if (trip.getStartPlace() != null && trip.getEndPlace() != null) {
            double directDistance = GeoUtils.calculateHaversineDistance(
                    trip.getStartPlace().getLatitudeCentroid(), trip.getStartPlace().getLongitudeCentroid(),
                    trip.getEndPlace().getLatitudeCentroid(), trip.getEndPlace().getLongitudeCentroid());
            trip.setEstimatedDistanceMeters(directDistance);
        } else {
            trip.setEstimatedDistanceMeters(totalDistance);
        }
    }

    private String getMostCommonTransportMode(List<Trip> trips) {
        Map<String, Integer> modeCounts = new HashMap<>();

        for (Trip trip : trips) {
            String mode = trip.getTransportModeInferred();
            if (mode != null) {
                modeCounts.put(mode, modeCounts.getOrDefault(mode, 0) + 1);
            }
        }

        // Find the mode with the highest count
        return modeCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("UNKNOWN");
    }
}
