package com.dedicatedcode.reitti.service.statistics;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.*;

/**
 * Determines the home for every night from the nights spent around it.
 * <p>
 * For each night, the nights inside a centered rolling window are counted per place. The place with the most nights
 * is home, so a vacation of a few weeks never replaces the home, while a move is picked up once the new place
 * dominates the window. A place marked as {@link com.dedicatedcode.reitti.model.geo.SignificantPlace.PlaceType#HOME}
 * is preferred, but only while it competes with the other places in the window. Marking the current home therefore
 * does not turn it into the home of years in which it was never visited.
 */
public class HomeResolver {

    public static final int WINDOW_DAYS = 45;
    public static final int MIN_NIGHTS = 10;
    public static final double MIN_SHARE = 0.3;
    public static final int HYSTERESIS_NIGHTS = 5;

    public enum Source {MARKED, INFERRED, NONE}

    public record HomeTimeline(Map<LocalDate, Long> homes, Set<Long> markedHomes, Long changingTo) implements Serializable {

        public Long homeFor(LocalDate date) {
            return homes.get(date);
        }

        public boolean isHome(LocalDate date, Long placeId) {
            return placeId != null && Objects.equals(homes.get(date), placeId);
        }

        public Source sourceOf(Long homeId) {
            if (homeId == null) {
                return Source.NONE;
            }
            return markedHomes.contains(homeId) ? Source.MARKED : Source.INFERRED;
        }
    }

    public HomeTimeline resolve(SortedMap<LocalDate, NightCalculator.Night> nights,
                                Set<Long> markedHomes,
                                LocalDate from,
                                LocalDate toInclusive) {
        if (nights.isEmpty()) {
            return new HomeTimeline(Map.of(), Set.copyOf(markedHomes), null);
        }

        LocalDate firstNight = nights.firstKey();
        LocalDate iterationStart = firstNight.isBefore(from) ? firstNight : from;

        Map<Long, Integer> windowCounts = new HashMap<>();
        int[] tracked = {0};
        for (LocalDate date = iterationStart.minusDays(WINDOW_DAYS); !date.isAfter(iterationStart.plusDays(WINDOW_DAYS)); date = date.plusDays(1)) {
            add(nights.get(date), windowCounts, tracked, 1);
        }

        Map<LocalDate, Long> homes = new HashMap<>();
        Long previous = null;
        for (LocalDate date = iterationStart; !date.isAfter(toInclusive); date = date.plusDays(1)) {
            if (date.isAfter(iterationStart)) {
                add(nights.get(date.plusDays(WINDOW_DAYS)), windowCounts, tracked, 1);
                add(nights.get(date.minusDays(WINDOW_DAYS + 1)), windowCounts, tracked, -1);
            }
            Long home = pickHome(windowCounts, tracked[0], markedHomes, previous);
            if (!date.isBefore(from)) {
                homes.put(date, home);
            }
            previous = home;
        }

        return new HomeTimeline(homes, Set.copyOf(markedHomes), detectChange(nights, toInclusive, homes));
    }

    private Long pickHome(Map<Long, Integer> windowCounts, int trackedNights, Set<Long> markedHomes, Long previous) {
        Long marked = null;
        int markedCount = 0;
        for (Long candidate : markedHomes) {
            int count = windowCounts.getOrDefault(candidate, 0);
            if (count > markedCount || (count == markedCount && count > 0 && candidate < marked)) {
                marked = candidate;
                markedCount = count;
            }
        }
        if (marked != null) {
            int bestCount = windowCounts.values().stream().max(Integer::compare).orElse(0);
            if (markedCount + HYSTERESIS_NIGHTS > bestCount) {
                return marked;
            }
        }
        return pickInferred(windowCounts, trackedNights, previous);
    }

    private Long pickInferred(Map<Long, Integer> windowCounts, int trackedNights, Long previous) {
        Map.Entry<Long, Integer> best = windowCounts.entrySet().stream()
                .max(Map.Entry.<Long, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .orElse(null);
        int previousCount = previous != null ? windowCounts.getOrDefault(previous, 0) : 0;
        if (best == null) {
            return null;
        }
        boolean bestQualifies = best.getValue() >= MIN_NIGHTS && best.getValue() >= MIN_SHARE * trackedNights;
        if (previous != null && !previous.equals(best.getKey())) {
            boolean previousQualifies = previousCount >= MIN_NIGHTS / 2;
            if (previousQualifies && (!bestQualifies || best.getValue() - previousCount < HYSTERESIS_NIGHTS)) {
                return previous;
            }
        }
        if (bestQualifies) {
            return best.getKey();
        }
        return previous != null && previousCount >= MIN_NIGHTS / 2 ? previous : null;
    }

    /**
     * The window is centered, so close to the end of the data only past nights are available. If the nights of the
     * trailing window point to another place, the home is probably about to change.
     */
    private Long detectChange(SortedMap<LocalDate, NightCalculator.Night> nights, LocalDate toInclusive, Map<LocalDate, Long> homes) {
        if (nights.isEmpty()) {
            return null;
        }
        LocalDate lastNight = nights.lastKey();
        LocalDate lastTracked = nights.values().stream()
                .filter(n -> n.state() == NightCalculator.State.AT_PLACE)
                .map(NightCalculator.Night::date)
                .reduce((a, b) -> b)
                .orElse(null);
        if (lastTracked == null || lastNight.isAfter(toInclusive.plusDays(WINDOW_DAYS))) {
            return null;
        }
        Long currentHome = homes.get(lastTracked);
        Map<Long, Integer> trailing = new HashMap<>();
        for (LocalDate date = lastTracked.minusDays(WINDOW_DAYS / 2); !date.isAfter(lastTracked); date = date.plusDays(1)) {
            NightCalculator.Night night = nights.get(date);
            if (night != null && night.state() == NightCalculator.State.AT_PLACE) {
                trailing.merge(night.placeId(), 1, Integer::sum);
            }
        }
        return trailing.entrySet().stream()
                .filter(e -> e.getValue() >= MIN_NIGHTS)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .filter(candidate -> !candidate.equals(currentHome))
                .orElse(null);
    }

    private static void add(NightCalculator.Night night, Map<Long, Integer> counts, int[] tracked, int delta) {
        if (night == null || night.state() != NightCalculator.State.AT_PLACE) {
            return;
        }
        counts.merge(night.placeId(), delta, Integer::sum);
        if (counts.get(night.placeId()) <= 0) {
            counts.remove(night.placeId());
        }
        tracked[0] += delta;
    }
}
