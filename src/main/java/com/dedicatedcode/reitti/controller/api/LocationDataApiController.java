package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.dto.LocationDataRequest;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.ApiToken;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class LocationDataApiController {
    
    private static final Logger logger = LoggerFactory.getLogger(LocationDataApiController.class);
    
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final UserJdbcService userJdbcService;
    
    @Autowired
    public LocationDataApiController(RawLocationPointJdbcService rawLocationPointJdbcService,
                                   UserJdbcService userJdbcService) {
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.userJdbcService = userJdbcService;
    }

    @GetMapping("/raw-location-points")
    public ResponseEntity<?> getRawLocationPointsForCurrentUser(@AuthenticationPrincipal User user,
                                                                @RequestParam(required = false) String date,
                                                                @RequestParam(required = false) String startDate,
                                                                @RequestParam(required = false) String endDate,
                                                                @RequestParam(required = false, defaultValue = "UTC") String timezone,
                                                                @RequestParam(required = false) Integer zoom) {
        return this.getRawLocationPoints(user.getId(), date, startDate, endDate, timezone, zoom);
    }

    @GetMapping("/raw-location-points/{userId}")
    public ResponseEntity<?> getRawLocationPoints(@PathVariable Long userId,
                                                  @RequestParam(required = false) String date,
                                                  @RequestParam(required = false) String startDate,
                                                  @RequestParam(required = false) String endDate,
                                                  @RequestParam(required = false, defaultValue = "UTC") String timezone,
                                                  @RequestParam(required = false) Integer zoom) {
        try {
            ZoneId userTimezone = ZoneId.of(timezone);
            Instant startOfRange;
            Instant endOfRange;

            // Support both single date and date range
            if (startDate != null && endDate != null) {
                // Date range mode
                LocalDate selectedStartDate = LocalDate.parse(startDate);
                LocalDate selectedEndDate = LocalDate.parse(endDate);

                startOfRange = selectedStartDate.atStartOfDay(userTimezone).toInstant();
                endOfRange = selectedEndDate.plusDays(1).atStartOfDay(userTimezone).toInstant().minusMillis(1);
            } else if (date != null) {
                // Single date mode (backward compatibility)
                LocalDate selectedDate = LocalDate.parse(date);

                startOfRange = selectedDate.atStartOfDay(userTimezone).toInstant();
                endOfRange = selectedDate.plusDays(1).atStartOfDay(userTimezone).toInstant().minusMillis(1);
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Either 'date' or both 'startDate' and 'endDate' must be provided"
                ));
            }

            // Get the user from the repository by userId
            User user = userJdbcService.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Get raw location points for the user and date range
            List<LocationDataRequest.LocationPoint> points = rawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(user, startOfRange, endOfRange).stream()
                .filter(point -> !point.getTimestamp().isBefore(startOfRange) && point.getTimestamp().isBefore(endOfRange))
                .sorted(Comparator.comparing(RawLocationPoint::getTimestamp))
                    .map(point -> {
                        LocationDataRequest.LocationPoint p = new LocationDataRequest.LocationPoint();
                        p.setLatitude(point.getLatitude());
                        p.setLongitude(point.getLongitude());
                        p.setAccuracyMeters(point.getAccuracyMeters());
                        p.setTimestamp(point.getTimestamp().toString());
                        return p;
                    })
                    .toList();

            // Apply Visvalingam-Whyatt simplification based on zoom level
            List<LocationDataRequest.LocationPoint> simplifiedPoints = simplifyPoints(points, zoom);

            return ResponseEntity.ok(Map.of("points", simplifiedPoints));
            
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Invalid date format. Expected format: YYYY-MM-DD"
            ));
        } catch (Exception e) {
            logger.error("Error fetching raw location points", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error fetching raw location points: " + e.getMessage()));
        }
    }

    /**
     * Simplify a list of location points using the Visvalingam-Whyatt algorithm
     * @param points The original list of points
     * @param zoom The current map zoom level (null means no simplification)
     * @return Simplified list of points
     */
    private List<LocationDataRequest.LocationPoint> simplifyPoints(List<LocationDataRequest.LocationPoint> points, Integer zoom) {
        // If zoom is not provided or points are too few, return original
        if (zoom == null || points.size() <= 2) {
            return points;
        }

        // Calculate target point count based on zoom level
        // Higher zoom = more detail = more points
        // Zoom levels typically range from 1-20
        int targetPointCount = calculateTargetPointCount(points.size(), zoom);
        
        if (targetPointCount >= points.size()) {
            return points; // No simplification needed
        }

        logger.debug("Simplifying {} points to {} points for zoom level {}", points.size(), targetPointCount, zoom);

        return visvalingamWhyatt(points, targetPointCount);
    }

    /**
     * Calculate target point count based on zoom level
     */
    private int calculateTargetPointCount(int originalCount, int zoom) {
        // Ensure we keep at least 10 points and at most the original count
        // Zoom 1-5: very simplified (10-20% of points)
        // Zoom 6-10: moderately simplified (20-40% of points)
        // Zoom 11-15: lightly simplified (40-70% of points)
        // Zoom 16+: minimal simplification (70-100% of points)
        
        double retentionRatio;
        if (zoom <= 5) {
            retentionRatio = 0.10 + (zoom - 1) * 0.025; // 10% to 20%
        } else if (zoom <= 10) {
            retentionRatio = 0.20 + (zoom - 6) * 0.04; // 20% to 40%
        } else if (zoom <= 15) {
            retentionRatio = 0.40 + (zoom - 11) * 0.06; // 40% to 70%
        } else {
            retentionRatio = 0.70 + Math.min(zoom - 16, 4) * 0.075; // 70% to 100%
        }

        int targetCount = (int) Math.ceil(originalCount * retentionRatio);
        return Math.max(10, Math.min(targetCount, originalCount));
    }

    /**
     * Visvalingam-Whyatt algorithm implementation for polyline simplification
     */
    private List<LocationDataRequest.LocationPoint> visvalingamWhyatt(List<LocationDataRequest.LocationPoint> points, int targetCount) {
        if (points.size() <= targetCount) {
            return points;
        }

        // Create a list of triangles with their effective areas
        List<Triangle> triangles = new ArrayList<>();
        
        // Initialize triangles for all interior points
        for (int i = 1; i < points.size() - 1; i++) {
            Triangle triangle = new Triangle(i - 1, i, i + 1, points);
            triangles.add(triangle);
        }

        // Use a priority queue to efficiently find the triangle with minimum area
        PriorityQueue<Triangle> heap = new PriorityQueue<>(Comparator.comparingDouble(t -> t.area));
        heap.addAll(triangles);

        // Track which points to keep
        Set<Integer> removedIndices = new HashSet<>();
        
        // Remove points until we reach the target count
        int pointsToRemove = points.size() - targetCount;
        
        while (pointsToRemove > 0 && !heap.isEmpty()) {
            Triangle minTriangle = heap.poll();
            
            // Skip if this triangle's center point was already removed
            if (removedIndices.contains(minTriangle.centerIndex)) {
                continue;
            }

            // Mark the center point for removal
            removedIndices.add(minTriangle.centerIndex);
            pointsToRemove--;

            // Update neighboring triangles
            updateNeighboringTriangles(heap, minTriangle, removedIndices, points);
        }

        // Build the result list with remaining points
        List<LocationDataRequest.LocationPoint> result = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            if (!removedIndices.contains(i)) {
                result.add(points.get(i));
            }
        }

        return result;
    }

    /**
     * Update neighboring triangles after removing a point
     */
    private void updateNeighboringTriangles(PriorityQueue<Triangle> heap, Triangle removed, 
                                           Set<Integer> removedIndices, List<LocationDataRequest.LocationPoint> points) {
        // Find the previous and next non-removed points
        int prevIndex = removed.leftIndex;
        while (prevIndex > 0 && removedIndices.contains(prevIndex)) {
            prevIndex--;
        }

        int nextIndex = removed.rightIndex;
        while (nextIndex < points.size() - 1 && removedIndices.contains(nextIndex)) {
            nextIndex++;
        }

        // Create new triangles if possible
        if (prevIndex > 0 && !removedIndices.contains(prevIndex - 1)) {
            Triangle newTriangle = new Triangle(prevIndex - 1, prevIndex, nextIndex, points);
            heap.add(newTriangle);
        }

        if (nextIndex < points.size() - 1 && !removedIndices.contains(nextIndex + 1)) {
            Triangle newTriangle = new Triangle(prevIndex, nextIndex, nextIndex + 1, points);
            heap.add(newTriangle);
        }
    }

    /**
     * Helper class to represent a triangle formed by three consecutive points
     */
    private static class Triangle {
        final int leftIndex;
        final int centerIndex;
        final int rightIndex;
        final double area;

        Triangle(int leftIndex, int centerIndex, int rightIndex, List<LocationDataRequest.LocationPoint> points) {
            this.leftIndex = leftIndex;
            this.centerIndex = centerIndex;
            this.rightIndex = rightIndex;
            this.area = calculateTriangleArea(
                points.get(leftIndex),
                points.get(centerIndex),
                points.get(rightIndex)
            );
        }

        /**
         * Calculate the area of a triangle formed by three points using the cross product
         */
        private static double calculateTriangleArea(LocationDataRequest.LocationPoint p1, 
                                                    LocationDataRequest.LocationPoint p2, 
                                                    LocationDataRequest.LocationPoint p3) {
            // Using the cross product formula for triangle area
            // Area = 0.5 * |x1(y2 - y3) + x2(y3 - y1) + x3(y1 - y2)|
            double area = Math.abs(
                p1.getLongitude() * (p2.getLatitude() - p3.getLatitude()) +
                p2.getLongitude() * (p3.getLatitude() - p1.getLatitude()) +
                p3.getLongitude() * (p1.getLatitude() - p2.getLatitude())
            ) / 2.0;
            
            return area;
        }
    }

    @GetMapping("/latest-location")
    public ResponseEntity<?> getLatestLocationForCurrentUser(@AuthenticationPrincipal User user,
                                                             @RequestParam(required = false) Instant since) {
        try {
            Optional<RawLocationPoint> latest;
            if (since == null) {
                latest = rawLocationPointJdbcService.findLatest(user);
            } else {
                latest = rawLocationPointJdbcService.findLatest(user, since);
            }

            if (latest.isEmpty()) {
                return ResponseEntity.ok(Map.of("hasLocation", false));
            }
            
            RawLocationPoint latestPoint = latest.get();
            
            LocationDataRequest.LocationPoint point = new LocationDataRequest.LocationPoint();
            point.setLatitude(latestPoint.getLatitude());
            point.setLongitude(latestPoint.getLongitude());
            point.setAccuracyMeters(latestPoint.getAccuracyMeters());
            point.setTimestamp(latestPoint.getTimestamp().toString());
            
            return ResponseEntity.ok(Map.of(
                "hasLocation", true,
                "point", point
            ));
            
        } catch (Exception e) {
            logger.error("Error fetching latest location", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error fetching latest location: " + e.getMessage()));
        }
    }
}
