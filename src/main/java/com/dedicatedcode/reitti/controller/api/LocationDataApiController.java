package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.dto.RawLocationDataResponse;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.MagicLinkAccessLevel;
import com.dedicatedcode.reitti.model.security.TokenUser;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.LocationPointsSimplificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class LocationDataApiController {
    
    private static final Logger logger = LoggerFactory.getLogger(LocationDataApiController.class);
    
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final LocationPointsSimplificationService simplificationService;
    private final UserJdbcService userJdbcService;
    
    @Autowired
    public LocationDataApiController(RawLocationPointJdbcService rawLocationPointJdbcService,
                                     LocationPointsSimplificationService simplificationService,
                                     UserJdbcService userJdbcService) {
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.simplificationService = simplificationService;
        this.userJdbcService = userJdbcService;
    }

    @GetMapping("/raw-location-points")
    public ResponseEntity<?> getRawLocationPointsForCurrentUser(@AuthenticationPrincipal User user,
                                                                @RequestParam(required = false) String date,
                                                                @RequestParam(required = false) String startDate,
                                                                @RequestParam(required = false) String endDate,
                                                                @RequestParam(required = false, defaultValue = "UTC") String timezone,
                                                                @RequestParam(required = false) Integer zoom,
                                                                @RequestParam(required = false) Double minLat,
                                                                @RequestParam(required = false) Double maxLat,
                                                                @RequestParam(required = false) Double minLng,
                                                                @RequestParam(required = false) Double maxLng) {
        return this.getRawLocationPoints(user, user.getId(), date, startDate, endDate, timezone, zoom,  minLat, maxLat, minLng, maxLng);
    }

    @GetMapping("/raw-location-points/{userId}")
    public ResponseEntity<?> getRawLocationPoints(@AuthenticationPrincipal User user,
                                                  @PathVariable Long userId,
                                                  @RequestParam(required = false) String date,
                                                  @RequestParam(required = false) String startDate,
                                                  @RequestParam(required = false) String endDate,
                                                  @RequestParam(required = false, defaultValue = "UTC") String timezone,
                                                  @RequestParam(required = false) Integer zoom,
                                                  @RequestParam(required = false) Double minLat,
                                                  @RequestParam(required = false) Double maxLat,
                                                  @RequestParam(required = false) Double minLng,
                                                  @RequestParam(required = false) Double maxLng) {
        try {
            ZoneId userTimezone = ZoneId.of(timezone);
            Instant startOfRange = null;
            Instant endOfRange = null;

            // Support both single date and date range
            if (startDate != null && endDate != null) {
                //first try to parse them as date time

                try {
                    LocalDateTime startTimestamp = LocalDateTime.parse(startDate);
                    LocalDateTime endTimestamp = LocalDateTime.parse(endDate);
                    startOfRange = startTimestamp.atZone(userTimezone).toInstant();
                    endOfRange = endTimestamp.atZone(userTimezone).toInstant();
                } catch (DateTimeParseException ignored) {
                }

                if (startOfRange == null && endOfRange == null) {
                    LocalDate selectedStartDate = LocalDate.parse(startDate);
                    LocalDate selectedEndDate = LocalDate.parse(endDate);
                    startOfRange = selectedStartDate.atStartOfDay(userTimezone).toInstant();
                    endOfRange = selectedEndDate.plusDays(1).atStartOfDay(userTimezone).toInstant().minusMillis(1);
                }
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


            boolean includeRawLocationPath = true;
            if (user instanceof TokenUser) {
                if (!Objects.equals(user.getId(), userId)) {
                    throw new IllegalAccessException("User not allowed to fetch raw location points for other users");
                }

                includeRawLocationPath = user.getAuthorities().stream().anyMatch(a ->
                        a.equals(MagicLinkAccessLevel.FULL_ACCESS.asAuthority()) ||
                        a.equals(MagicLinkAccessLevel.ONLY_LIVE.asAuthority()) ||
                        a.equals(MagicLinkAccessLevel.ONLY_LIVE_WITH_PHOTOS.asAuthority()));
            }
            // Get the user from the repository by userId
            User userToFetchDataFrom = userJdbcService.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

            List<RawLocationDataResponse.Segment> result;
            if (includeRawLocationPath) {
                List<List<LocationPoint>> segments = loadSegmentsInBoundingBoxAndTime(userToFetchDataFrom, minLat, maxLat, minLng, maxLng, startOfRange, endOfRange);
                result = segments.stream().map(s -> {
                    List<LocationPoint> simplifiedPoints = simplificationService.simplifyPoints(s, zoom);
                    return new RawLocationDataResponse.Segment(simplifiedPoints);
                }).toList();
            } else {
                result = Collections.emptyList();
            }

            Optional<RawLocationPoint> latest = this.rawLocationPointJdbcService.findLatest(userToFetchDataFrom);
            return ResponseEntity.ok(new RawLocationDataResponse(result, latest.map(this::toLocationPoint).orElse(null)));
            
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
            
            LocationPoint point = new LocationPoint();
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

    private List<List<LocationPoint>> loadSegmentsInBoundingBoxAndTime(User user, Double minLat, Double maxLat, Double minLng, Double maxLng, Instant startOfRange, Instant endOfRange) {
        List<RawLocationPoint> pointsInBoxWithNeighbors;
        if (minLat == null || maxLat == null || minLng == null || maxLng == null) {
            pointsInBoxWithNeighbors = this.rawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(user, startOfRange, endOfRange);
        } else {
            pointsInBoxWithNeighbors = this.rawLocationPointJdbcService.findPointsInBoxWithNeighbors(user, startOfRange, endOfRange, minLat, maxLat, minLng, maxLng);
        }
        return extractPathSegments(pointsInBoxWithNeighbors, minLat, maxLat, minLng, maxLng);
    }

    private List<List<LocationPoint>> extractPathSegments(List<RawLocationPoint> points, Double minLat, Double maxLat, Double minLng, Double maxLng) {
        List<List<LocationPoint>> segments = new ArrayList<>();
        List<LocationPoint> currentPath = new ArrayList<>();
        int consecutiveOutside = 0;

        for (RawLocationPoint point : points) {
            boolean inBox = isPointInBox(point, minLat, maxLat, minLng, maxLng);

            if (inBox) {
                currentPath.add(toLocationPoint(point));
                consecutiveOutside = 0;
            } else {
                consecutiveOutside++;
                currentPath.add(toLocationPoint(point));

                if (consecutiveOutside == 2) {
                    currentPath.removeLast();

                    if (currentPath.size() >= 2) {
                        segments.add(new ArrayList<>(currentPath));
                    }
                    currentPath.clear();
                    consecutiveOutside = 0;
                }
            }
        }

        if (currentPath.size() >= 2) {
            segments.add(new ArrayList<>(currentPath));
        }

        return segments;
    }

    private boolean isPointInBox(RawLocationPoint point, Double minLat, Double maxLat, Double minLng, Double maxLng) {
        if (minLat == null || maxLat == null || minLng == null || maxLng == null) {
            return true;
        }
        return point.getLatitude() >= minLat &&
                point.getLatitude() <= maxLat &&
                point.getLongitude() >= minLng &&
                point.getLongitude() <= maxLng;
    }

    private LocationPoint toLocationPoint(RawLocationPoint point) {
        LocationPoint p = new LocationPoint();
        p.setLatitude(point.getLatitude());
        p.setLongitude(point.getLongitude());
        p.setAccuracyMeters(point.getAccuracyMeters());
        p.setTimestamp(point.getTimestamp().toString());
        return p;
    }

}
