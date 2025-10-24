package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.geo.TransportModeConfig;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class TransportModeService {

    private final TransportModeJdbcService transportModeJdbcService;

    public TransportModeService(TransportModeJdbcService transportModeJdbcService) {
        this.transportModeJdbcService = transportModeJdbcService;
    }

    public TransportMode inferTransportMode(User user, double distanceMeters, Instant startTime, Instant endTime) {

        List<TransportModeConfig> config = transportModeJdbcService.getTransportModeConfigs(user);

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

        for (TransportModeConfig transportModeConfig : config) {
            if (transportModeConfig.maxKmh() == null || transportModeConfig.maxKmh() > speedKmh)
                return transportModeConfig.mode();
        }

        return TransportMode.UNKNOWN;
    }
}
