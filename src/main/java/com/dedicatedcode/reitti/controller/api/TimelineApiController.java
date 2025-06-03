package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.dto.TimelineResponse;
import com.dedicatedcode.reitti.model.*;
import com.dedicatedcode.reitti.repository.ProcessedVisitRepository;
import com.dedicatedcode.reitti.repository.RawLocationPointRepository;
import com.dedicatedcode.reitti.repository.TripRepository;
import com.dedicatedcode.reitti.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/timeline")
public class TimelineApiController {

    private final UserRepository userRepository;
    private final ProcessedVisitRepository processedVisitRepository;
    private final TripRepository tripRepository;
    private final RawLocationPointRepository rawLocationPointRepository;

    @Autowired
    public TimelineApiController(UserRepository userRepository,
                                 ProcessedVisitRepository processedVisitRepository,
                                 TripRepository tripRepository, RawLocationPointRepository rawLocationPointRepository) {
        this.userRepository = userRepository;
        this.processedVisitRepository = processedVisitRepository;
        this.tripRepository = tripRepository;
        this.rawLocationPointRepository = rawLocationPointRepository;
    }

    @GetMapping
    public ResponseEntity<TimelineResponse> getTimeline(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate selectedDate,
            @RequestParam(required = false, defaultValue = "1") Long userId) {
        
        // Default to today if no date is provided
        LocalDate date = selectedDate != null ? selectedDate : LocalDate.now();
        
        // Find the user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        
        // Convert LocalDate to start and end Instant for the selected date
        Instant startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusMillis(1);
        
        // Get processed visits for the user and date range
        List<ProcessedVisit> processedVisits = processedVisitRepository.findByUserAndTimeOverlap(
                user, startOfDay, endOfDay);
        
        // Get trips for the user and date range
        List<Trip> trips = tripRepository.findByUserAndTimeOverlap(
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
            
            if (trip.getStartPlace() != null) {
                SignificantPlace start = trip.getStartPlace();
                startPlace = new TimelineResponse.PlaceInfo(
                        start.getId(),
                        start.getName() != null ? start.getName() : "Unknown Place",
                        start.getAddress(),
                        start.getCategory(),
                        start.getLatitudeCentroid(),
                        start.getLongitudeCentroid()
                );
            }
            
            if (trip.getEndPlace() != null) {
                SignificantPlace end = trip.getEndPlace();
                endPlace = new TimelineResponse.PlaceInfo(
                        end.getId(),
                        end.getName() != null ? end.getName() : "Unknown Place",
                        end.getAddress(),
                        end.getCategory(),
                        end.getLatitudeCentroid(),
                        end.getLongitudeCentroid()
                );
            }
            List<RawLocationPoint> path = this.rawLocationPointRepository.findByUserAndTimestampBetweenOrderByTimestampAsc(trip.getUser(), trip.getStartTime(), trip.getEndTime());
            entries.add(new TimelineResponse.TimelineEntry(
                    "TRIP",
                    trip.getId(),
                    trip.getStartTime(),
                    trip.getEndTime(),
                    trip.getDurationSeconds(),
                    null,
                    startPlace,
                    endPlace,
                    trip.getEstimatedDistanceMeters(),
                    trip.getTransportModeInferred(),
                    path.stream().map(p -> new TimelineResponse.PointInfo(p.getLatitude(), p.getLongitude(), p.getTimestamp(), p.getAccuracyMeters())).toList()

            ));
        }
        
        // Sort entries by start time
        entries.sort((e1, e2) -> e1.getStartTime().compareTo(e2.getStartTime()));
        
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
