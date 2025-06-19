package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.dto.TimelineResponse;
import com.dedicatedcode.reitti.model.*;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/timeline")
public class TimelineApiController {

    private final UserJdbcService userJdbcService;
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final TripJdbcService tripJdbcService;
    private final RawLocationPointJdbcService rawLocationPointJdbcService;

    @Autowired
    public TimelineApiController(UserJdbcService userJdbcService,
                                 ProcessedVisitJdbcService processedVisitJdbcService,
                                 TripJdbcService tripJdbcService,
                                 RawLocationPointJdbcService rawLocationPointJdbcService) {
        this.userJdbcService = userJdbcService;
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.tripJdbcService = tripJdbcService;
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
    }

    @GetMapping
    public ResponseEntity<TimelineResponse> getTimeline(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate selectedDate,
            @RequestParam(required = false, defaultValue = "1") Long userId) {

        // Default to today if no date is provided
        LocalDate date = selectedDate != null ? selectedDate : LocalDate.now();

        // Find the user
        User user = userJdbcService.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Convert LocalDate to start and end Instant for the selected date
        Instant startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusMillis(1);

        // Get processed visits for the user and date range
        List<ProcessedVisit> processedVisits = processedVisitJdbcService.findByUserAndTimeOverlap(
                user, startOfDay, endOfDay);

        // Get trips for the user and date range
        List<Trip> trips = tripJdbcService.findByUserAndTimeOverlap(
                user, startOfDay, endOfDay);

        // Convert to timeline entries
        List<TimelineResponse.TimelineEntry> entries = new ArrayList<>();

        // Add visits to timeline
        for (ProcessedVisit visit : processedVisits) {
            SignificantPlace place = visit.getPlace();
            if (place != null) {
                TimelineResponse.PlaceInfo placeInfo = new TimelineResponse.PlaceInfo(
                        place.getId(),
                        place.getName() != null ? place.getName() : "Unknown Place",
                        place.getAddress(),
                        place.getCategory(),
                        place.getLatitudeCentroid(),
                        place.getLongitudeCentroid()
                );

                entries.add(new TimelineResponse.TimelineEntry(
                        "VISIT",
                        visit.getId(),
                        visit.getStartTime(),
                        visit.getEndTime(),
                        visit.getDurationSeconds(),
                        placeInfo,
                        null,
                        null,
                        null,
                        null,
                        null
                ));
            }
        }

        // Add trips to timeline
        for (Trip trip : trips) {
            TimelineResponse.PlaceInfo startPlace = null;
            TimelineResponse.PlaceInfo endPlace = null;

            if (trip.getStartVisit().getPlace() != null) {
                SignificantPlace start = trip.getStartVisit().getPlace();
                startPlace = new TimelineResponse.PlaceInfo(
                        start.getId(),
                        start.getName() != null ? start.getName() : "Unknown Place",
                        start.getAddress(),
                        start.getCategory(),
                        start.getLatitudeCentroid(),
                        start.getLongitudeCentroid()
                );
            }

            if (trip.getEndVisit().getPlace() != null) {
                SignificantPlace end = trip.getEndVisit().getPlace();
                endPlace = new TimelineResponse.PlaceInfo(
                        end.getId(),
                        end.getName() != null ? end.getName() : "Unknown Place",
                        end.getAddress(),
                        end.getCategory(),
                        end.getLatitudeCentroid(),
                        end.getLongitudeCentroid()
                );
            }
            List<RawLocationPoint> path = this.rawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(user, trip.getStartTime(), trip.getEndTime());
            List<TimelineResponse.PointInfo> pathPoints = new ArrayList<>();
            pathPoints.add(new TimelineResponse.PointInfo(trip.getStartVisit().getPlace().getLatitudeCentroid(), trip.getStartVisit().getPlace().getLongitudeCentroid(), trip.getStartTime(), 0.0));
            pathPoints.addAll(path.stream().map(p -> new TimelineResponse.PointInfo(p.getLatitude(), p.getLongitude(), p.getTimestamp(), p.getAccuracyMeters())).toList());
            pathPoints.add(new TimelineResponse.PointInfo(trip.getEndVisit().getPlace().getLatitudeCentroid(), trip.getEndVisit().getPlace().getLongitudeCentroid(), trip.getEndTime(), 0.0));

            entries.add(new TimelineResponse.TimelineEntry(
                    "TRIP",
                    trip.getId(),
                    trip.getStartTime(),
                    trip.getEndTime(),
                    trip.getDurationSeconds(),
                    null,
                    startPlace,
                    endPlace,
                    trip.getTravelledDistanceMeters() != null ? trip.getTravelledDistanceMeters() : trip.getEstimatedDistanceMeters(),
                    trip.getTransportModeInferred(),
                    pathPoints

            ));
        }

        // Sort entries by start time
        entries.sort(Comparator.comparing(TimelineResponse.TimelineEntry::getStartTime));

        return ResponseEntity.ok(new TimelineResponse(entries));
    }

    @GetMapping("/today")
    public ResponseEntity<TimelineResponse> getToday(@RequestParam(required = false, defaultValue = "1") Long userId) {
        return getTimeline(LocalDate.now(), userId);
    }

    @GetMapping("/prev-day")
    public ResponseEntity<TimelineResponse> getPreviousDay(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate selectedDate,
            @RequestParam(required = false, defaultValue = "1") Long userId) {
        return getTimeline(selectedDate.minusDays(1), userId);
    }

    @GetMapping("/next-day")
    public ResponseEntity<TimelineResponse> getNextDay(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate selectedDate,
            @RequestParam(required = false, defaultValue = "1") Long userId) {
        return getTimeline(selectedDate.plusDays(1), userId);
    }
}
