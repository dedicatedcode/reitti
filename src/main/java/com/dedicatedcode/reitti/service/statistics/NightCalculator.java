package com.dedicatedcode.reitti.service.statistics;

import java.io.Serializable;
import java.time.*;
import java.util.*;

/**
 * Assigns every night in a date range to the place where it was spent. The night of a date D is the
 * window from 22:00 on D until 06:00 on D+1 in the timezone of the place (or the fallback zone).
 */
public class NightCalculator {

    static final LocalTime NIGHT_START = LocalTime.of(22, 0);
    static final LocalTime NIGHT_END = LocalTime.of(6, 0);
    static final long MIN_NIGHT_SECONDS = Duration.ofHours(3).getSeconds();
    static final long MAX_BRIDGED_GAP_SECONDS = Duration.ofHours(16).getSeconds();

    public enum State {AT_PLACE, IN_TRANSIT, UNTRACKED}

    public record Night(LocalDate date, State state, Long placeId, boolean bridged) implements Serializable {
        public boolean isAt(Long otherPlaceId) {
            return state == State.AT_PLACE && Objects.equals(placeId, otherPlaceId);
        }
    }

    public static Instant nightStart(LocalDate date, ZoneId zone) {
        return date.atTime(NIGHT_START).atZone(zone).toInstant();
    }

    public static Instant nightEnd(LocalDate date, ZoneId zone) {
        return date.plusDays(1).atTime(NIGHT_END).atZone(zone).toInstant();
    }

    public SortedMap<LocalDate, Night> calculate(List<StatisticsData.Visit> visits,
                                                 List<StatisticsData.Trip> trips,
                                                 Map<Long, StatisticsData.Place> places,
                                                 ZoneId fallbackZone,
                                                 LocalDate from,
                                                 LocalDate toInclusive) {
        Map<LocalDate, Map<Long, Long>> placeSeconds = new HashMap<>();
        Map<LocalDate, Long> transitSeconds = new HashMap<>();

        for (StatisticsData.Visit visit : visits) {
            ZoneId zone = zoneOf(places.get(visit.placeId()), fallbackZone);
            LocalDate first = visit.start().atZone(zone).toLocalDate().minusDays(1);
            LocalDate last = visit.end().atZone(zone).toLocalDate();
            for (LocalDate date = max(first, from); !date.isAfter(last) && !date.isAfter(toInclusive); date = date.plusDays(1)) {
                long overlap = visit.overlapSeconds(nightStart(date, zone), nightEnd(date, zone));
                if (overlap > 0) {
                    placeSeconds.computeIfAbsent(date, _ -> new HashMap<>()).merge(visit.placeId(), overlap, Long::sum);
                }
            }
        }

        for (StatisticsData.Trip trip : trips) {
            ZoneId zone = trip.startPlaceId() != null ? zoneOf(places.get(trip.startPlaceId()), fallbackZone) : fallbackZone;
            LocalDate first = trip.start().atZone(zone).toLocalDate().minusDays(1);
            LocalDate last = trip.end().atZone(zone).toLocalDate();
            for (LocalDate date = max(first, from); !date.isAfter(last) && !date.isAfter(toInclusive); date = date.plusDays(1)) {
                long overlap = trip.overlapSeconds(nightStart(date, zone), nightEnd(date, zone));
                if (overlap > 0) {
                    transitSeconds.merge(date, overlap, Long::sum);
                }
            }
        }

        SortedMap<LocalDate, Night> result = new TreeMap<>();
        for (LocalDate date = from; !date.isAfter(toInclusive); date = date.plusDays(1)) {
            Map<Long, Long> perPlace = placeSeconds.getOrDefault(date, Map.of());
            Map.Entry<Long, Long> best = perPlace.entrySet().stream()
                    .max(Map.Entry.<Long, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                    .orElse(null);
            long transit = transitSeconds.getOrDefault(date, 0L);
            if (best != null && best.getValue() >= MIN_NIGHT_SECONDS && best.getValue() >= transit) {
                result.put(date, new Night(date, State.AT_PLACE, best.getKey(), false));
            } else if (transit >= MIN_NIGHT_SECONDS) {
                result.put(date, new Night(date, State.IN_TRANSIT, null, false));
            } else {
                Long bridged = findBridgedPlace(visits, trips, places, fallbackZone, date);
                if (bridged != null) {
                    result.put(date, new Night(date, State.AT_PLACE, bridged, true));
                } else {
                    result.put(date, new Night(date, State.UNTRACKED, null, false));
                }
            }
        }
        return result;
    }

    /**
     * Phones often stop recording at night. If the last visit before the night and the first visit after it
     * are at the same place, and no trip happened in between, the night is attributed to that place.
     */
    private Long findBridgedPlace(List<StatisticsData.Visit> visits,
                                  List<StatisticsData.Trip> trips,
                                  Map<Long, StatisticsData.Place> places,
                                  ZoneId fallbackZone,
                                  LocalDate date) {
        Instant windowStart = nightStart(date, fallbackZone);
        Instant windowEnd = nightEnd(date, fallbackZone);
        int beforeIndex = lastIndexStartingAtOrBefore(visits, windowStart);
        if (beforeIndex < 0 || beforeIndex + 1 >= visits.size()) {
            return null;
        }
        StatisticsData.Visit before = visits.get(beforeIndex);
        StatisticsData.Visit after = visits.get(beforeIndex + 1);
        if (before.placeId() != after.placeId() || after.end().isBefore(windowEnd)) {
            return null;
        }
        ZoneId zone = zoneOf(places.get(before.placeId()), fallbackZone);
        if (!before.end().isBefore(nightEnd(date, zone)) || !after.start().isAfter(nightStart(date, zone))) {
            return null;
        }
        long gap = Duration.between(before.end(), after.start()).getSeconds();
        if (gap > MAX_BRIDGED_GAP_SECONDS) {
            return null;
        }
        Instant gapStart = before.end();
        Instant gapEnd = after.start();
        int tripIndex = Math.max(0, lastTripStartingAtOrBefore(trips, gapStart));
        for (int i = tripIndex; i < trips.size() && trips.get(i).start().isBefore(gapEnd); i++) {
            if (trips.get(i).overlapSeconds(gapStart, gapEnd) > 0) {
                return null;
            }
        }
        return before.placeId();
    }

    private static int lastIndexStartingAtOrBefore(List<StatisticsData.Visit> visits, Instant instant) {
        int low = 0;
        int high = visits.size() - 1;
        int result = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (!visits.get(mid).start().isAfter(instant)) {
                result = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return result;
    }

    private static int lastTripStartingAtOrBefore(List<StatisticsData.Trip> trips, Instant instant) {
        int low = 0;
        int high = trips.size() - 1;
        int result = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (!trips.get(mid).start().isAfter(instant)) {
                result = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return result;
    }

    static ZoneId zoneOf(StatisticsData.Place place, ZoneId fallback) {
        return place != null && place.timezone() != null ? place.timezone() : fallback;
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}
