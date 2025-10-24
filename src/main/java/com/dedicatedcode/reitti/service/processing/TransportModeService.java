package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TransportModeService {


    public TransportMode inferTransportMode(User user, double distanceMeters, Instant startTime, Instant endTime) {
        // Calculate duration in seconds
        long durationSeconds = endTime.getEpochSecond() - startTime.getEpochSecond();

        // Avoid division by zero
        if (durationSeconds <= 0) {
            return TransportMode.UNKNOWN;
        }

        // Calculate speed in meters per second
        double speedMps = distanceMeters / durationSeconds;

        // Convert to km/h for easier interpretation
        double speedKmh = speedMps * 3.6;

        // Simple transport mode inference based on average speed
        if (speedKmh < 7) {
            return TransportMode.WALKING;
        } else if (speedKmh < 20) {
            return TransportMode.CYCLING;
        } else if (speedKmh < 120) {
            return TransportMode.DRIVING;
        } else {
            return TransportMode.TRANSIT;
        }
    }
}
