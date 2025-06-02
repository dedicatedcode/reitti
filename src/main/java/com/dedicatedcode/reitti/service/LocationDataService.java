package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.model.RawLocationPoint;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.RawLocationPointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class LocationDataService {
    private static final Logger logger = LoggerFactory.getLogger(LocationDataService.class);

    private final RawLocationPointRepository rawLocationPointRepository;

    @Autowired
    public LocationDataService(RawLocationPointRepository rawLocationPointRepository) {
        this.rawLocationPointRepository = rawLocationPointRepository;
    }

    @Transactional
    public List<RawLocationPoint> processLocationData(User user, List<LocationDataRequest.LocationPoint> points) {
        List<RawLocationPoint> savedPoints = new ArrayList<>();
        int duplicatesSkipped = 0;

        for (LocationDataRequest.LocationPoint point : points) {
            try {
                Optional<RawLocationPoint> savedPoint = processSingleLocationPoint(user, point);
                if (savedPoint.isPresent()) {
                    savedPoints.add(savedPoint.get());
                } else {
                    duplicatesSkipped++;
                }
            } catch (Exception e) {
                logger.warn("Error processing point at timestamp {}: {}", point.getTimestamp(), e.getMessage());
            }
        }

        if (duplicatesSkipped > 0) {
            logger.info("Skipped {} duplicate points for user {}", duplicatesSkipped, user.getUsername());
        }

        return savedPoints;
    }

    @Transactional
    public Optional<RawLocationPoint> processSingleLocationPoint(User user, LocationDataRequest.LocationPoint point) {
        ZonedDateTime parse = ZonedDateTime.parse(point.getTimestamp());
        Instant timestamp = parse.toInstant();

        // Check if a point with this timestamp already exists for this user
        Optional<RawLocationPoint> existingPoint = rawLocationPointRepository.findByUserAndTimestamp(user, timestamp);

        if (existingPoint.isPresent()) {
            logger.debug("Skipping duplicate point at timestamp {} for user {}", timestamp, user.getUsername());
            return Optional.empty(); // Return empty to indicate no new point was saved
        }

        RawLocationPoint locationPoint = new RawLocationPoint();
        locationPoint.setUser(user);
        locationPoint.setLatitude(point.getLatitude());
        locationPoint.setLongitude(point.getLongitude());
        locationPoint.setTimestamp(timestamp);
        locationPoint.setAccuracyMeters(point.getAccuracyMeters());
        locationPoint.setActivityProvided(point.getActivity());

        return Optional.of(rawLocationPointRepository.save(locationPoint));
    }
}
