package com.dedicatedcode.reitti.service.statistics;

import com.dedicatedcode.reitti.model.geo.GeoUtils;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.service.TimeUtil;

import java.time.*;
import java.util.*;

public class DayCalculator {

    static final long UNTRACKED_BELOW_SECONDS = Duration.ofHours(1).getSeconds();
    static final long THIN_BELOW_SECONDS = Duration.ofHours(12).getSeconds();
    static final double TRAVEL_DISTANCE_METERS = 100_000;
    static final long FAMILIAR_AFTER_DAYS = 30;
    static final long WORK_STAY_SECONDS = Duration.ofHours(3).getSeconds();

    public enum DayType {COMMUTE, WANDER, TRAVEL, HOME, UNTRACKED}

    public static final class Day {
        public final LocalDate date;
        public final Instant start;
        public final Instant end;
        public final Long homeId;
        public long homeSeconds;
        public long placeSeconds;
        public long movingSeconds;
        public double distanceMeters;
        public final Map<TransportMode, Double> modeDistance = new EnumMap<>(TransportMode.class);
        public final Map<TransportMode, Long> modeSeconds = new EnumMap<>(TransportMode.class);
        public final Map<TransportMode, Integer> modeTrips = new EnumMap<>(TransportMode.class);
        public final Map<Long, Long> placeTime = new HashMap<>();
        public final Map<Long, Integer> placeVisits = new HashMap<>();
        public LocalTime firstDeparture;
        public LocalTime lastReturn;
        public int outings;
        public int stops;
        public int trips;
        public int modeChangeTrips;
        public int walksFromHome;
        public double maxDistanceFromHomeMeters;
        public Long farthestPlaceId;
        public int newPlaces;
        public long unfamiliarSeconds;
        public double longestWalkMeters;
        public DayType type;

        Day(LocalDate date, Instant start, Instant end, Long homeId) {
            this.date = date;
            this.start = start;
            this.end = end;
            this.homeId = homeId;
        }

        public long lengthSeconds() {
            return Duration.between(start, end).getSeconds();
        }

        public long coveredSeconds() {
            return Math.min(lengthSeconds(), homeSeconds + placeSeconds + movingSeconds);
        }

        public long noDataSeconds() {
            return Math.max(0, lengthSeconds() - coveredSeconds());
        }

        public boolean isTracked() {
            return coveredSeconds() >= UNTRACKED_BELOW_SECONDS;
        }

        public boolean isThin() {
            return isTracked() && coveredSeconds() < THIN_BELOW_SECONDS;
        }

        public boolean isWeekend() {
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
        }

        public long visitSeconds() {
            return homeSeconds + placeSeconds;
        }
    }

    public List<Day> calculate(LocalDate from,
                               LocalDate toExclusive,
                               ZoneId zone,
                               LocalTime dayStartTime,
                               List<StatisticsData.Visit> visits,
                               List<StatisticsData.Trip> trips,
                               Map<Long, StatisticsData.Place> places,
                               Map<Long, Instant> firstVisits,
                               HomeResolver.HomeTimeline homes) {
        List<Day> days = new ArrayList<>();
        int visitIndex = 0;
        int tripIndex = 0;
        for (LocalDate date = from; date.isBefore(toExclusive); date = date.plusDays(1)) {
            Instant start = TimeUtil.startOfDay(date, zone, dayStartTime);
            Instant end = TimeUtil.startOfDay(date.plusDays(1), zone, dayStartTime);
            Day day = new Day(date, start, end, homes.homeFor(date));
            StatisticsData.Place home = day.homeId != null ? places.get(day.homeId) : null;

            while (visitIndex < visits.size() && !visits.get(visitIndex).end().isAfter(start)) {
                visitIndex++;
            }
            for (int i = visitIndex; i < visits.size() && visits.get(i).start().isBefore(end); i++) {
                applyVisit(day, visits.get(i), places, home, firstVisits);
            }

            while (tripIndex < trips.size() && !trips.get(tripIndex).end().isAfter(start)) {
                tripIndex++;
            }
            for (int i = tripIndex; i < trips.size() && trips.get(i).start().isBefore(end); i++) {
                applyTrip(day, trips.get(i), zone);
            }

            days.add(day);
        }
        return days;
    }

    private void applyVisit(Day day, StatisticsData.Visit visit, Map<Long, StatisticsData.Place> places,
                            StatisticsData.Place home, Map<Long, Instant> firstVisits) {
        long overlap = visit.overlapSeconds(day.start, day.end);
        if (overlap <= 0) {
            return;
        }
        boolean atHome = day.homeId != null && day.homeId == visit.placeId();
        if (atHome) {
            day.homeSeconds += overlap;
        } else {
            day.placeSeconds += overlap;
            if (!visit.start().isBefore(day.start)) {
                day.stops++;
            }
        }
        day.placeTime.merge(visit.placeId(), overlap, Long::sum);
        if (!visit.start().isBefore(day.start)) {
            day.placeVisits.merge(visit.placeId(), 1, Integer::sum);
        }

        Instant firstVisit = firstVisits.get(visit.placeId());
        if (firstVisit != null) {
            if (!firstVisit.isBefore(day.start) && firstVisit.isBefore(day.end) && !visit.start().isAfter(firstVisit)) {
                day.newPlaces++;
            }
            if (!atHome && firstVisit.isAfter(day.start.minus(Duration.ofDays(FAMILIAR_AFTER_DAYS)))) {
                day.unfamiliarSeconds += overlap;
            }
        }

        StatisticsData.Place place = places.get(visit.placeId());
        if (home != null && place != null && !atHome) {
            double distance = GeoUtils.distanceInMeters(home.latitude(), home.longitude(), place.latitude(), place.longitude());
            if (distance > day.maxDistanceFromHomeMeters) {
                day.maxDistanceFromHomeMeters = distance;
                day.farthestPlaceId = place.id();
            }
        }
    }

    private void applyTrip(Day day, StatisticsData.Trip trip, ZoneId zone) {
        long overlap = trip.overlapSeconds(day.start, day.end);
        if (overlap <= 0) {
            return;
        }
        day.movingSeconds += overlap;
        long duration = trip.durationSeconds();
        double fraction = duration > 0 ? (double) overlap / duration : 1.0;
        day.distanceMeters += trip.distanceMeters() * fraction;

        double segmentDistanceSum = trip.segments().stream().mapToDouble(StatisticsData.Segment::distanceMeters).sum();
        for (StatisticsData.Segment segment : trip.segments()) {
            Instant segmentStart = trip.start().plusSeconds(segment.offsetSeconds());
            Instant segmentEnd = segmentStart.plusSeconds(segment.durationSeconds());
            long segmentOverlap = StatisticsData.overlapSeconds(segmentStart, segmentEnd, day.start, day.end);
            if (segmentOverlap <= 0) {
                continue;
            }
            double segmentFraction = segment.durationSeconds() > 0 ? (double) segmentOverlap / segment.durationSeconds() : 1.0;
            double segmentDistance = segmentDistanceSum > 0
                    ? segment.distanceMeters()
                    : (duration > 0 ? trip.distanceMeters() * segment.durationSeconds() / duration : 0);
            day.modeDistance.merge(segment.mode(), segmentDistance * segmentFraction, Double::sum);
            day.modeSeconds.merge(segment.mode(), segmentOverlap, Long::sum);
        }

        if (trip.start().isBefore(day.start)) {
            return;
        }
        TransportMode dominant = trip.dominantMode();
        day.trips++;
        day.modeTrips.merge(dominant, 1, Integer::sum);
        if (trip.distinctModes() > 1) {
            day.modeChangeTrips++;
        }
        if (dominant == TransportMode.WALKING && trip.distanceMeters() > day.longestWalkMeters) {
            day.longestWalkMeters = trip.distanceMeters();
        }
        boolean fromHome = day.homeId != null && day.homeId.equals(trip.startPlaceId());
        boolean toHome = day.homeId != null && day.homeId.equals(trip.endPlaceId());
        if (fromHome) {
            day.outings++;
            LocalTime departure = trip.start().atZone(zone).toLocalTime();
            if (day.firstDeparture == null) {
                day.firstDeparture = departure;
            }
        }
        if (toHome && !trip.end().isAfter(day.end)) {
            day.lastReturn = trip.end().atZone(zone).toLocalTime();
        }
        if (fromHome && toHome && dominant == TransportMode.WALKING) {
            day.walksFromHome++;
        }
    }

    public Set<Long> findCommutePlaces(List<Day> days) {
        List<Day> trackedWeekdays = days.stream().filter(d -> d.isTracked() && !d.isWeekend()).toList();
        Map<Long, Integer> longStays = new HashMap<>();
        for (Day day : trackedWeekdays) {
            day.placeTime.forEach((placeId, seconds) -> {
                if (!placeId.equals(day.homeId) && seconds >= WORK_STAY_SECONDS) {
                    longStays.merge(placeId, 1, Integer::sum);
                }
            });
        }
        Set<Long> result = new HashSet<>();
        longStays.forEach((placeId, count) -> {
            if (count >= 4 && count >= trackedWeekdays.size() * 0.25) {
                result.add(placeId);
            }
        });
        return result;
    }

    public void classify(List<Day> days, SortedMap<LocalDate, NightCalculator.Night> nights,
                         Map<Long, StatisticsData.Place> places, Set<Long> commutePlaces) {
        for (Day day : days) {
            day.type = classify(day, nights, places, commutePlaces);
        }
    }

    private DayType classify(Day day, SortedMap<LocalDate, NightCalculator.Night> nights,
                             Map<Long, StatisticsData.Place> places, Set<Long> commutePlaces) {
        if (!day.isTracked()) {
            return DayType.UNTRACKED;
        }
        if (isAway(nights.get(day.date.minusDays(1)), day.homeId) || isAway(nights.get(day.date), day.homeId)
                || day.maxDistanceFromHomeMeters >= TRAVEL_DISTANCE_METERS) {
            return DayType.TRAVEL;
        }
        if (day.homeId != null && day.placeSeconds < Duration.ofMinutes(15).getSeconds() && day.trips <= 1) {
            return DayType.HOME;
        }
        if (!day.isWeekend()) {
            for (Map.Entry<Long, Long> entry : day.placeTime.entrySet()) {
                StatisticsData.Place place = places.get(entry.getKey());
                boolean longWorkStay = place != null && place.type() == SignificantPlace.PlaceType.WORK && entry.getValue() >= WORK_STAY_SECONDS;
                if (commutePlaces.contains(entry.getKey()) || longWorkStay) {
                    return DayType.COMMUTE;
                }
            }
        }
        if (day.homeId == null && day.trips == 0) {
            return DayType.HOME;
        }
        return DayType.WANDER;
    }

    static boolean isAway(NightCalculator.Night night, Long homeId) {
        if (night == null || homeId == null) {
            return false;
        }
        return night.state() == NightCalculator.State.IN_TRANSIT
                || (night.state() == NightCalculator.State.AT_PLACE && !night.placeId().equals(homeId));
    }
}
