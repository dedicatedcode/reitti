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
        List<ProcessedVisit> processedVisits = processedVisitJdbcService.findByUserAndTimeOverlap(user, startOfDay, endOfDay);
        List<Trip> trips = tripJdbcService.findByUserAndTimeOverlap(user, startOfDay, endOfDay);

        // Get user settings for unit system and connected accounts
        UserSettings userSettings = userSettingsJdbcService.findByUserId(user.getId())
                .orElse(UserSettings.defaultSettings(user.getId()));
        try {
            return buildTimelineEntries(user, processedVisits, trips, userTimeZone, selectedDate, userSettings);
        } catch (JsonProcessingException e) {
            log.error("Unable to build timeline entries.", e);
            return Collections.emptyList();
        }
    }

    /**
     * Build timeline entries from processed visits and trips
     */
    private List<TimelineEntry> buildTimelineEntries(User user, List<ProcessedVisit> processedVisits, List<Trip> trips, ZoneId timezone, LocalDate selectedDate, UserSettings userSettings) throws JsonProcessingException {
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
                entry.setStartTimezone(visit.getPlace().getTimezone());
                entry.setEndTime(visit.getEndTime());
                entry.setEndTimezone(visit.getPlace().getTimezone());
                entry.setFormattedTimeRange(formatTimeRange(visit.getStartTime(), visit.getEndTime(), timezone, selectedDate));
                entry.setFormattedLocalTimeRange(formatTimeRange(visit.getStartTime(), visit.getEndTime(), visit.getPlace().getTimezone(), selectedDate));
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
            entry.setStartTimezone(trip.getStartVisit().getPlace().getTimezone());
            entry.setEndTime(trip.getEndTime());
            entry.setEndTimezone(trip.getEndVisit().getPlace().getTimezone());
            entry.setFormattedTimeRange(formatTimeRange(trip.getStartTime(), trip.getEndTime(), timezone, selectedDate));
            entry.setFormattedDuration(formatDuration(trip.getStartTime(), trip.getEndTime()));
            entry.setFormattedLocalTimeRange(formatTimeRange(trip.getStartTime(), trip.getEndTime(), trip.getStartVisit().getPlace().getTimezone(), trip.getEndVisit().getPlace().getTimezone(), selectedDate));

            List<RawLocationPoint> path = this.rawLocationPointJdbcService.findByUserAndTimestampBetweenOrderByTimestampAsc(user, trip.getStartTime(), trip.getEndTime());
            List<PointInfo> pathPoints = new ArrayList<>();
            pathPoints.add(new PointInfo(trip.getStartVisit().getPlace().getLatitudeCentroid(), trip.getStartVisit().getPlace().getLongitudeCentroid(), trip.getStartTime(), 0.0));
            pathPoints.addAll(path.stream().map(p -> new PointInfo(p.getLatitude(), p.getLongitude(), p.getTimestamp(), p.getAccuracyMeters())).toList());
            pathPoints.add(new PointInfo(trip.getEndVisit().getPlace().getLatitudeCentroid(), trip.getEndVisit().getPlace().getLongitudeCentroid(), trip.getEndTime(), 0.0));

            entry.setPath(objectMapper.writeValueAsString(pathPoints));
            if (trip.getTravelledDistanceMeters() != null) {
                entry.setDistanceMeters(trip.getTravelledDistanceMeters());
                entry.setFormattedDistance(formatDistance(trip.getTravelledDistanceMeters(), userSettings.getUnitSystem()));
            } else if (trip.getEstimatedDistanceMeters() != null) {
                entry.setDistanceMeters(trip.getEstimatedDistanceMeters());
                entry.setFormattedDistance(formatDistance(trip.getEstimatedDistanceMeters(), userSettings.getUnitSystem()));
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

    private String formatTimeRange(Instant startTime, Instant endTime, ZoneId startTimezone, ZoneId endTimezone, LocalDate selectedDate) {
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("MMM d HH:mm");

        LocalDate startDate = startTime.atZone(startTimezone).toLocalDate();
        LocalDate endDate = endTime.atZone(endTimezone).toLocalDate();
        LocalDate selectedDateInStartTimezone = selectedDate.atTime(10,0).atZone(startTimezone).toLocalDate();
        LocalDate selectedDateInEndTimezone = selectedDate.atTime(10,0).atZone(endTimezone).toLocalDate();
        String start, end;

        // If start time is not on the selected date, show date + time
        if (!startDate.equals(selectedDateInStartTimezone)) {
            start = startTime.atZone(startTimezone).format(dateTimeFormatter);
        } else {
            start = startTime.atZone(startTimezone).format(timeFormatter);
        }

        // If end time is not on the selected date, show date + time
        if (!endDate.equals(selectedDateInEndTimezone)) {
            end = endTime.atZone(endTimezone).format(dateTimeFormatter);
        } else {
            end = endTime.atZone(endTimezone).format(timeFormatter);
        }

        return start + " - " + end;
    }

    private String formatTimeRange(Instant startTime, Instant endTime, ZoneId timezone, LocalDate selectedDate) {
        return formatTimeRange(startTime, endTime, timezone, timezone, selectedDate);
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
