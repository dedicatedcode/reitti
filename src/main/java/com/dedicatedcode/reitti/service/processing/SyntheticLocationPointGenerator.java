package com.dedicatedcode.reitti.service.processing;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.GeoUtils;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static java.time.temporal.ChronoUnit.SECONDS;

@Service
public class SyntheticLocationPointGenerator {
    
    private static final Logger logger = LoggerFactory.getLogger(SyntheticLocationPointGenerator.class);
    
    public List<LocationPoint> generateSyntheticPoints(
            RawLocationPoint startPoint, 
            RawLocationPoint endPoint, 
            int targetPointsPerMinute,
            double maxDistanceMeters) {
        
        if (!shouldInterpolate(startPoint, endPoint, maxDistanceMeters)) {
            logger.trace("Skipping interpolation between points: distance too large or other constraints not met");
            return List.of();
        }
        
        List<LocationPoint> syntheticPoints = new ArrayList<>();
        
        // Calculate target interval in seconds
        int intervalSeconds = 60 / targetPointsPerMinute;
        
        Instant startTime = startPoint.getTimestamp();
        Instant endTime = endPoint.getTimestamp();
        
        // Generate points at regular intervals, excluding the endpoints
        Instant currentTime = startTime.plusSeconds(intervalSeconds).truncatedTo(SECONDS);
        
        while (currentTime.isBefore(endTime)) {
            // Calculate interpolation ratio (0.0 to 1.0)
            long totalDuration = endTime.getEpochSecond() - startTime.getEpochSecond();
            long currentDuration = currentTime.getEpochSecond() - startTime.getEpochSecond();
            double timeRatio = (double) currentDuration / totalDuration;

            // Calculate speed and adjust the ratio based on it
            double distance = GeoUtils.distanceInMeters(startPoint, endPoint);
            double speed = distance / totalDuration; // meters per second

            // Apply speed-based transformation: slower speeds shift points towards start
            // Using a more aggressive power function to cluster points at the beginning
            double speedFactor = Math.min(speed / 2.0, 1.0); // normalize to 2 m/s as reference (more sensitive)
            double exponent = 1.0 / (speedFactor * 0.8 + 0.1); // More aggressive exponent (higher values = more clustering at start)
            double ratio = Math.pow(timeRatio, exponent);

            // Interpolate coordinates
            GeoPoint interpolatedCoords = interpolateCoordinates(
                startPoint.getGeom(),
                endPoint.getGeom(), 
                ratio
            );
            
            // Interpolate accuracy and elevation
            Double interpolatedAccuracy = interpolateValue(
                startPoint.getAccuracyMeters(), 
                endPoint.getAccuracyMeters(), 
                ratio
            );
            
            Double interpolatedElevation = interpolateValue(
                startPoint.getElevationMeters(), 
                endPoint.getElevationMeters(), 
                ratio
            );
            
            // Create synthetic LocationPoint
            LocationPoint syntheticPoint = new LocationPoint();
            syntheticPoint.setLatitude(interpolatedCoords.latitude());
            syntheticPoint.setLongitude(interpolatedCoords.longitude());
            syntheticPoint.setTimestamp(currentTime);
            syntheticPoint.setAccuracyMeters(interpolatedAccuracy);
            syntheticPoint.setElevationMeters(interpolatedElevation);
            
            syntheticPoints.add(syntheticPoint);
            
            currentTime = currentTime.plusSeconds(intervalSeconds);
        }
        
        logger.trace("Generated {} synthetic points between {} and {}",
            syntheticPoints.size(), startTime, endTime);
        
        return syntheticPoints;
    }
    
    /**
     * Generates a stationary cluster of synthetic points anchored at the start point of a gap.
     * Used when the distance between the two surrounding real points is too large for interpolation
     * but the gap is long enough to assume the user stayed in place (e.g. device was turned off
     * during a longer stay). Points are generated at the regular interval, with a small
     * deterministic jitter around the start point so the cluster does not collapse into a single
     * coordinate.
     */
    public List<LocationPoint> generateStationaryPoints(
            RawLocationPoint startPoint,
            RawLocationPoint endPoint,
            int targetPointsPerMinute,
            double jitterRadiusMeters) {

        List<LocationPoint> syntheticPoints = new ArrayList<>();

        int intervalSeconds = 60 / targetPointsPerMinute;

        Instant startTime = startPoint.getTimestamp();
        Instant endTime = endPoint.getTimestamp();

        double latOffset = GeoUtils.metersToDegreesAtPosition(jitterRadiusMeters, startPoint.getGeom().latitude());
        double lonOffset = GeoUtils.metersToDegreesAtPosition(jitterRadiusMeters, startPoint.getGeom().latitude());

        Instant currentTime = startTime.plusSeconds(intervalSeconds).truncatedTo(SECONDS);
        int index = 0;
        while (currentTime.isBefore(endTime)) {
            double jitterLat = Math.sin(index * 12.9898) * latOffset;
            double jitterLon = Math.cos(index * 78.233) * lonOffset;

            LocationPoint syntheticPoint = new LocationPoint();
            syntheticPoint.setLatitude(startPoint.getGeom().latitude() + jitterLat);
            syntheticPoint.setLongitude(startPoint.getGeom().longitude() + jitterLon);
            syntheticPoint.setTimestamp(currentTime);
            syntheticPoint.setAccuracyMeters(startPoint.getAccuracyMeters());
            syntheticPoint.setElevationMeters(startPoint.getElevationMeters());

            syntheticPoints.add(syntheticPoint);

            currentTime = currentTime.plusSeconds(intervalSeconds);
            index++;
        }

        logger.trace("Generated {} stationary synthetic points between {} and {}",
                syntheticPoints.size(), startTime, endTime);

        return syntheticPoints;
    }

    private boolean shouldInterpolate(RawLocationPoint start, RawLocationPoint end, double maxDistance) {
        // Check if the distance between points is within an acceptable range
        double distance = GeoUtils.distanceInMeters(start, end);
        
        if (distance > maxDistance) {
            logger.trace("Distance {} meters exceeds maximum interpolation distance {} meters",
                distance, maxDistance);
            return false;
        }
        
        return true;
    }
    
    private GeoPoint interpolateCoordinates(GeoPoint start, GeoPoint end, double ratio) {
        // Use linear interpolation for coordinates
        // For more accuracy over long distances, could use great circle interpolation
        double lat = start.latitude() + (end.latitude() - start.latitude()) * ratio;
        double lon = start.longitude() + (end.longitude() - start.longitude()) * ratio;
        
        return new GeoPoint(lat, lon);
    }
    
    private Double interpolateValue(Double start, Double end, double ratio) {
        if (start == null && end == null) {
            return null;
        }
        if (start == null) {
            return end;
        }
        if (end == null) {
            return start;
        }
        
        return start + (end - start) * ratio;
    }
}
