package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.dto.ConnectedUserAccount;
import com.dedicatedcode.reitti.model.*;
import com.dedicatedcode.reitti.repository.*;
import com.dedicatedcode.reitti.service.AvatarService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/timeline")
public class TimelineController {

    private static final Logger log = LoggerFactory.getLogger(TimelineController.class);
    private final RawLocationPointJdbcService rawLocationPointJdbcService;
    private final SignificantPlaceJdbcService placeService;
    private final UserJdbcService userJdbcService;
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final TripJdbcService tripJdbcService;
    private final UserSettingsJdbcService userSettingsJdbcService;
    private final AvatarService avatarService;
    private final ObjectMapper objectMapper;

    @Autowired
    public TimelineController(RawLocationPointJdbcService rawLocationPointJdbcService,
                              SignificantPlaceJdbcService placeService,
                              UserJdbcService userJdbcService,
                              ProcessedVisitJdbcService processedVisitJdbcService,
                              TripJdbcService tripJdbcService,
                              UserSettingsJdbcService userSettingsJdbcService, AvatarService avatarService,
                              ObjectMapper objectMapper) {
        this.rawLocationPointJdbcService = rawLocationPointJdbcService;
        this.placeService = placeService;
        this.userJdbcService = userJdbcService;
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.tripJdbcService = tripJdbcService;
        this.userSettingsJdbcService = userSettingsJdbcService;
        this.avatarService = avatarService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/content")
    public String getTimelineContent(@RequestParam String date, 
                                   @RequestParam(required = false, defaultValue = "UTC") String timezone,
                                   Principal principal, Model model) throws JsonProcessingException {
        LocalDate selectedDate = LocalDate.parse(date);
        ZoneId userTimezone = ZoneId.of(timezone);
        
        // Find the user by username
        User user = userJdbcService.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        
        // Convert LocalDate to start and end Instant for the selected date in user's timezone
        Instant startOfDay = selectedDate.atStartOfDay(userTimezone).toInstant();
        Instant endOfDay = selectedDate.plusDays(1).atStartOfDay(userTimezone).toInstant().minusMillis(1);
        
        // Get processed visits and trips for the user and date range
        List<ProcessedVisit> processedVisits = processedVisitJdbcService.findByUserAndTimeOverlap(
                user, startOfDay, endOfDay);
        List<Trip> trips = tripJdbcService.findByUserAndTimeOverlap(
                user, startOfDay, endOfDay);
        
        // Get user settings for unit system and connected accounts
        UserSettings userSettings = userSettingsJdbcService.findByUserId(user.getId())
                .orElse(UserSettings.defaultSettings(user.getId()));
        
        // Build timeline data for current user and connected users
        List<UserTimelineData> allUsersData = new ArrayList<>();
        
        // Add current user data first
        List<TimelineEntry> currentUserEntries = buildTimelineEntries(user, processedVisits, trips, userTimezone, selectedDate, userSettings.getUnitSystem());

        String currentUserAvatarUrl = this.avatarService.getInfo(user.getId()).map(avatarInfo -> String.format("/avatars/%d?ts=%s", user.getId(), avatarInfo.updatedAt())).orElse(String.format("/avatars/%d", user.getId()));
        String currentUserRawLocationPointsUrl = String.format("/api/v1/raw-location-points/%d?date=%s&timezone=%s", user.getId(), date, timezone);
        String currentUserInitials = generateInitials(user.getDisplayName());
        allUsersData.add(new UserTimelineData(user.getId(), user.getUsername(), currentUserInitials, currentUserAvatarUrl, null, currentUserEntries, currentUserRawLocationPointsUrl));
        
        // Add connected users data, sorted by username
        List<ConnectedUserAccount> connectedAccounts = userSettings.getConnectedUserAccounts();

        // Sort connected users by username
        connectedAccounts.sort(Comparator.comparing(ConnectedUserAccount::userId));
        
        for (ConnectedUserAccount connectedUserAccount : connectedAccounts) {
            Optional<User> connectedUserOpt = this.userJdbcService.findById(connectedUserAccount.userId());
            if (connectedUserOpt.isEmpty()) {
                log.warn("Could not find user with id {}", connectedUserAccount.userId());
                continue;
            }
            User connectedUser = connectedUserOpt.get();
            // Get connected user's timeline data for the same date
            List<ProcessedVisit> connectedVisits = processedVisitJdbcService.findByUserAndTimeOverlap(
                    connectedUser, startOfDay, endOfDay);
            List<Trip> connectedTrips = tripJdbcService.findByUserAndTimeOverlap(
                    connectedUser, startOfDay, endOfDay);
            
            // Get connected user's unit system
            UserSettings connectedUserSettings = userSettingsJdbcService.findByUserId(connectedUser.getId())
                    .orElse(UserSettings.defaultSettings(connectedUser.getId()));
            
            List<TimelineEntry> connectedUserEntries = buildTimelineEntries(connectedUser, connectedVisits, connectedTrips, userTimezone, selectedDate, connectedUserSettings.getUnitSystem());

            String connectedUserAvatarUrl = this.avatarService.getInfo(user.getId()).map(avatarInfo -> String.format("/avatars/%d?ts=%s", connectedUser.getId(), avatarInfo.updatedAt())).orElse(String.format("/avatars/%d", connectedUser.getId()));
            String connectedUserRawLocationPointsUrl = String.format("/api/v1/raw-location-points/%d?date=%s&timezone=%s", connectedUser.getId(), date, timezone);
            String connectedUserInitials = generateInitials(connectedUser.getDisplayName());
            
            allUsersData.add(new UserTimelineData(connectedUser.getId(), connectedUser.getDisplayName(), connectedUserInitials, connectedUserAvatarUrl, connectedUserAccount.color(), connectedUserEntries, connectedUserRawLocationPointsUrl));
        }
        
        // Create timeline data record
        TimelineData timelineData = new TimelineData(allUsersData);
        
        model.addAttribute("timelineData", timelineData);
        return "fragments/timeline :: timeline-content";
    }

    /**
     * Generate initials from a display name for avatar fallback
     */
    private String generateInitials(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            return "";
        }
        
        String trimmed = displayName.trim();
        
        // If display name contains whitespace, take first char of each word
        if (trimmed.contains(" ")) {
            StringBuilder initials = new StringBuilder();
            String[] words = trimmed.split("\\s+");
            for (String word : words) {
                if (!word.isEmpty()) {
                    initials.append(Character.toUpperCase(word.charAt(0)));
                }
            }
            return initials.toString();
        } else {
            // No whitespace - take first two letters, or just one if that's all there is
            if (trimmed.length() >= 2) {
                return (Character.toUpperCase(trimmed.charAt(0)) + "" + Character.toUpperCase(trimmed.charAt(1)));
            } else {
                return Character.toUpperCase(trimmed.charAt(0)) + "";
            }
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


    public record PointInfo(Double latitude, Double longitude, Instant timestamp, Double accuracy) {
    }
    
    public record TimelineData(
        List<UserTimelineData> users
    ) {}
    
    public record UserTimelineData(
        long userId,
        String displayName,
        String avatarFallback,
        String userAvatarUrl,
        String baseColor,
        List<TimelineEntry> entries,
        String rawLocationPointsUrl
    ) {}
    /**
     * Inner class to represent timeline entries for the template
     */
    public static class TimelineEntry {
        public enum Type { VISIT, TRIP }
        
        private String id;
        private Type type;
        private SignificantPlace place;
        private String path;
        private Instant startTime;
        private Instant endTime;
        private String formattedTimeRange;
        private String formattedDuration;
        private Double distanceMeters;
        private String formattedDistance;
        private String transportMode;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public Type getType() { return type; }
        public void setType(Type type) { this.type = type; }
        
        public SignificantPlace getPlace() { return place; }
        public void setPlace(SignificantPlace place) { this.place = place; }
        
        public Instant getStartTime() { return startTime; }
        public void setStartTime(Instant startTime) { this.startTime = startTime; }
        
        public Instant getEndTime() { return endTime; }
        public void setEndTime(Instant endTime) { this.endTime = endTime; }
        
        public String getFormattedTimeRange() { return formattedTimeRange; }
        public void setFormattedTimeRange(String formattedTimeRange) { this.formattedTimeRange = formattedTimeRange; }
        
        public String getFormattedDuration() { return formattedDuration; }
        public void setFormattedDuration(String formattedDuration) { this.formattedDuration = formattedDuration; }
        
        public Double getDistanceMeters() { return distanceMeters; }
        public void setDistanceMeters(Double distanceMeters) { this.distanceMeters = distanceMeters; }
        
        public String getFormattedDistance() { return formattedDistance; }
        public void setFormattedDistance(String formattedDistance) { this.formattedDistance = formattedDistance; }
        
        public String getTransportMode() { return transportMode; }
        public void setTransportMode(String transportMode) { this.transportMode = transportMode; }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        
    }

    @GetMapping("/places/edit-form/{id}")
    public String getPlaceEditForm(@PathVariable Long id, Model model) {
        SignificantPlace place = placeService.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("place", place);
        return "fragments/place-edit :: edit-form";
    }

    @PutMapping("/places/{id}")
    public String updatePlace(@PathVariable Long id, @RequestParam String name, Model model) {
        SignificantPlace place = placeService.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("place", placeService.update(place.withName(name)));
        return "fragments/place-edit :: view-mode";
    }

    @GetMapping("/places/view/{id}")
    public String getPlaceView(@PathVariable Long id, Model model) {
        SignificantPlace place = placeService.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("place", place);
        return "fragments/place-edit :: view-mode";
    }
}
