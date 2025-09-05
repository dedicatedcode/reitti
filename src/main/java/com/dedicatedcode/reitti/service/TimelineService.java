package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.dto.PointInfo;
import com.dedicatedcode.reitti.dto.TimelineEntry;
import com.dedicatedcode.reitti.model.*;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSettings;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
public class TimelineService {
    private static final Logger log = LoggerFactory.getLogger(TimelineService.class);
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final TripJdbcService tripJdbcService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final ObjectMapper objectMapper;

    public TimelineService(RawLocationPointJdbcService rawLocationPointJdbcService,
                           ProcessedVisitJdbcService processedVisitJdbcService,
                           TripJdbcService tripJdbcService,
                           UserSettingsJdbcService userSettingsJdbcService,
                           ObjectMapper objectMapper) {
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.tripJdbcService = tripJdbcService;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.objectMapper = objectMapper;
    }

    public List<TimelineEntry> buildTimelineEntries(User user, ZoneId userTimeZone, LocalDate selectedDate, Instant startOfDay, Instant endOfDay) {

        // Get processed visits and trips for the user and date range
        List<ProcessedVisit> processedVisits = processedVisitJdbcService.findByUserAndTimeOverlap(
                user, startOfDay, endOfDay);
        List<Trip> trips = tripJdbcService.findByUserAndTimeOverlap(
                user, startOfDay, endOfDay);

        // Get user settings for unit system and connected accounts
        UserSettings userSettings = userSettingsJdbcService.findByUserId(user.getId())
                .orElse(UserSettings.defaultSettings(user.getId()));
        try {
            return buildTimelineEntries(user, processedVisits, trips, userTimeZone, selectedDate, userSettings.getUnitSystem());
        } catch (JsonProcessingException e) {
            log.error("Unable to build timeline entries.", e);
            return Collections.emptyList();
        }
    }

    /**
     * Build timeline entries from processed visits and trips
     */
    private List<TimelineEntry> buildTimelineEntries(User user, List<ProcessedVisit> processedVisits, List<Trip> trips, ZoneId timezone, LocalDate selectedDate, UnitSystem unitSystem) throws JsonProcessingException {
        List<TimelineEntry> entries = new ArrayList<>();

        // Add processed visits to timeline
        for (ProcessedVisit visit : processedVisits) {
            SignificantPlace place = visit.getPlace();
            if (place != null) {
                TimelineEntry entry = new TimelineEntry();
                entry.setId("visit-" + visit.getId());
                entry.setType(TimelineEntry.Type.VISIT);
                entry.setPlace(place);
                entry.setStartTime(visit.getStartTime());
                entry.setEndTime(visit.getEndTime());
                entry.setFormattedTimeRange(formatTimeRange(visit.getStartTime(), visit.getEndTime(), timezone, selectedDate));
                entry.setFormattedDuration(formatDuration(visit.getStartTime(), visit.getEndTime()));
                entries.add(entry);
            }
        }

        // Add trips to timeline
        for (Trip trip : trips) {
            TimelineEntry entry = new TimelineEntry();
            entry.setId("trip-" + trip.getId());
            entry.setType(TimelineEntry.Type.TRIP);
            entry.setStartTime(trip.getStartTime());
            entry.setEndTime(trip.getEndTime());
            entry.setFormattedTimeRange(formatTimeRange(trip.getStartTime(), trip.getEndTime(), timezone, selectedDate));
            entry.setFormattedDuration(formatDuration(trip.getStartTime(), trip.getEndTime()));

            List<RawLocationPoint> path = this.rawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(user, trip.getStartTime(), trip.getEndTime());
            List<PointInfo> pathPoints = new ArrayList<>();
            pathPoints.add(new PointInfo(trip.getStartVisit().getPlace().getLatitudeCentroid(), trip.getStartVisit().getPlace().getLongitudeCentroid(), trip.getStartTime(), 0.0));
            pathPoints.addAll(path.stream().map(p -> new PointInfo(p.getLatitude(), p.getLongitude(), p.getTimestamp(), p.getAccuracyMeters())).toList());
            pathPoints.add(new PointInfo(trip.getEndVisit().getPlace().getLatitudeCentroid(), trip.getEndVisit().getPlace().getLongitudeCentroid(), trip.getEndTime(), 0.0));

            entry.setPath(objectMapper.writeValueAsString(pathPoints));
            if (trip.getTravelledDistanceMeters() != null) {
                entry.setDistanceMeters(trip.getTravelledDistanceMeters());
                entry.setFormattedDistance(formatDistance(trip.getTravelledDistanceMeters(), unitSystem));
            } else if (trip.getEstimatedDistanceMeters() != null) {
                entry.setDistanceMeters(trip.getEstimatedDistanceMeters());
                entry.setFormattedDistance(formatDistance(trip.getEstimatedDistanceMeters(), unitSystem));
            }

            if (trip.getTransportModeInferred() != null) {
                entry.setTransportMode(trip.getTransportModeInferred());
            }

            entries.add(entry);
        }

        // Sort timeline entries by start time
        entries.sort(Comparator.comparing(TimelineEntry::getStartTime));

        return entries;
    }


    /**
     * Format time range for display
     */
    private String formatTimeRange(Instant startTime, Instant endTime, ZoneId timezone, LocalDate selectedDate) {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("MMM d HH:mm");

        LocalDate startDate = startTime.atZone(timezone).toLocalDate();
        LocalDate endDate = endTime.atZone(timezone).toLocalDate();

        String start, end;

        // If start time is not on the selected date, show date + time
        if (!startDate.equals(selectedDate)) {
            start = startTime.atZone(timezone).format(dateTimeFormatter);
        } else {
            start = startTime.atZone(timezone).format(timeFormatter);
        }

        // If end time is not on the selected date, show date + time
        if (!endDate.equals(selectedDate)) {
            end = endTime.atZone(timezone).format(dateTimeFormatter);
        } else {
            end = endTime.atZone(timezone).format(timeFormatter);
        }

        return start + " - " + end;
    }

    /**
     * Format duration for display (this is a simple implementation, you might want to use HumanizeDuration)
     */
    private String formatDuration(Instant startTime, Instant endTime) {
        long durationMinutes = java.time.Duration.between(startTime, endTime).toMinutes();
        long hours = durationMinutes / 60;
        long minutes = durationMinutes % 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + "min";
        }
    }

    /**
     * Format distance according to unit system
     */
    private String formatDistance(Double distanceMeters, UnitSystem unitSystem) {
        if (distanceMeters == null) {
            return "";
        }

        switch (unitSystem) {
            case METRIC:
                if (distanceMeters >= 1000) {
                    return String.format("%.1f km", distanceMeters / 1000.0);
                } else {
                    return String.format("%.0f m", distanceMeters);
                }
            case IMPERIAL:
                double distanceFeet = distanceMeters * 3.28084;
                if (distanceFeet >= 5280) {
                    double distanceMiles = distanceFeet / 5280.0;
                    return String.format("%.1f mi", distanceMiles);
                } else {
                    return String.format("%.0f ft", distanceFeet);
                }
            default:
                return String.format("%.1f km", distanceMeters / 1000.0);
        }
    }

}
