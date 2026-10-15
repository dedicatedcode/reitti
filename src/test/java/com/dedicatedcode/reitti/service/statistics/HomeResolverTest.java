package com.dedicatedcode.reitti.service.statistics;

import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class HomeResolverTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final long HOME = 1L;
    private static final long VACATION = 2L;
    private static final long NEW_HOME = 3L;

    private final NightCalculator nightCalculator = new NightCalculator();
    private final HomeResolver resolver = new HomeResolver();

    @Test
    void threeWeekVacationDoesNotReplaceHome() {
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate vacationStart = LocalDate.of(2026, 7, 5);
        LocalDate vacationEnd = vacationStart.plusDays(21);
        LocalDate end = LocalDate.of(2026, 9, 30);
        SortedMap<LocalDate, NightCalculator.Night> nights = nights(start, end,
                                                                    date -> !date.isBefore(vacationStart) && date.isBefore(vacationEnd) ? VACATION : HOME);

        HomeResolver.HomeTimeline timeline = resolver.resolve(nights, Set.of(), start, end);

        assertEquals(HomeResolver.Source.INFERRED, timeline.sourceOf(timeline.homeFor(vacationStart)));
        for (LocalDate date = vacationStart; date.isBefore(vacationEnd); date = date.plusDays(1)) {
            assertEquals(HOME, timeline.homeFor(date), "home on " + date);
        }
        assertNull(timeline.changingTo());
    }

    @Test
    void moveIsDetectedAroundTheMoveDate() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate move = LocalDate.of(2026, 4, 15);
        LocalDate end = LocalDate.of(2026, 8, 31);
        SortedMap<LocalDate, NightCalculator.Night> nights = nights(start, end, date -> date.isBefore(move) ? HOME : NEW_HOME);

        HomeResolver.HomeTimeline timeline = resolver.resolve(nights, Set.of(), start, end);

        assertEquals(HOME, timeline.homeFor(move.minusDays(10)));
        assertEquals(NEW_HOME, timeline.homeFor(move.plusDays(10)));
        assertEquals(NEW_HOME, timeline.homeFor(end));
    }

    @Test
    void markedHomeIsNotUsedBeforeItWasLivedIn() {
        LocalDate start = LocalDate.of(2013, 1, 1);
        LocalDate move = LocalDate.of(2020, 3, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        SortedMap<LocalDate, NightCalculator.Night> nights = nights(start, end, date -> date.isBefore(move) ? HOME : NEW_HOME);

        HomeResolver.HomeTimeline timeline = resolver.resolve(nights, Set.of(NEW_HOME), start, end);

        LocalDate june2013 = LocalDate.of(2013, 6, 15);
        assertEquals(HOME, timeline.homeFor(june2013));
        assertEquals(HomeResolver.Source.INFERRED, timeline.sourceOf(timeline.homeFor(june2013)));
        assertEquals(NEW_HOME, timeline.homeFor(LocalDate.of(2024, 6, 15)));
        assertEquals(HomeResolver.Source.MARKED, timeline.sourceOf(timeline.homeFor(LocalDate.of(2024, 6, 15))));
        assertTrue(Math.abs(firstDateWithHome(timeline, NEW_HOME, start, end).toEpochDay() - move.toEpochDay()) <= 5);
    }

    @Test
    void markedHomeIsIgnoredWithoutNightsThere() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 3, 31);
        SortedMap<LocalDate, NightCalculator.Night> nights = nights(start, end, _ -> VACATION);

        HomeResolver.HomeTimeline timeline = resolver.resolve(nights, Set.of(HOME), start, end);

        assertEquals(VACATION, timeline.homeFor(LocalDate.of(2026, 2, 1)));
    }

    @Test
    void markedHomeWinsCloseCalls() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 3, 31);
        SortedMap<LocalDate, NightCalculator.Night> nights = nights(start, end, date -> date.getDayOfMonth() % 2 == 0 ? HOME : VACATION);

        HomeResolver.HomeTimeline timeline = resolver.resolve(nights, Set.of(HOME), start, end);

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            assertEquals(HOME, timeline.homeFor(date), "home on " + date);
        }
    }

    @Test
    void markedHomeKeepsVacationsAway() {
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate vacationStart = LocalDate.of(2026, 7, 5);
        LocalDate vacationEnd = vacationStart.plusDays(21);
        LocalDate end = LocalDate.of(2026, 9, 30);
        SortedMap<LocalDate, NightCalculator.Night> nights = nights(start, end,
                                                                    date -> !date.isBefore(vacationStart) && date.isBefore(vacationEnd) ? VACATION : HOME);

        HomeResolver.HomeTimeline timeline = resolver.resolve(nights, Set.of(HOME), start, end);

        for (LocalDate date = vacationStart; date.isBefore(vacationEnd); date = date.plusDays(1)) {
            assertEquals(HOME, timeline.homeFor(date), "home on " + date);
        }
    }

    private static LocalDate firstDateWithHome(HomeResolver.HomeTimeline timeline, long home, LocalDate start, LocalDate end) {
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (Long.valueOf(home).equals(timeline.homeFor(date))) {
                return date;
            }
        }
        throw new AssertionError("home never found");
    }

    @Test
    void multipleMarkedHomesFollowTheNights() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate move = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2026, 7, 31);
        SortedMap<LocalDate, NightCalculator.Night> nights = nights(start, end, date -> date.isBefore(move) ? HOME : NEW_HOME);

        HomeResolver.HomeTimeline timeline = resolver.resolve(nights, Set.of(HOME, NEW_HOME), start, end);

        assertEquals(HOME, timeline.homeFor(LocalDate.of(2026, 2, 1)));
        assertEquals(NEW_HOME, timeline.homeFor(LocalDate.of(2026, 6, 1)));
    }

    @Test
    void sparseDataYieldsNoHome() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 3, 31);
        SortedMap<LocalDate, NightCalculator.Night> nights = new TreeMap<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            boolean tracked = date.getDayOfMonth() == 1;
            nights.put(date, tracked
                    ? new NightCalculator.Night(date, NightCalculator.State.AT_PLACE, HOME, false)
                    : new NightCalculator.Night(date, NightCalculator.State.UNTRACKED, null, false));
        }

        HomeResolver.HomeTimeline timeline = resolver.resolve(nights, Set.of(), start, end);

        assertNull(timeline.homeFor(LocalDate.of(2026, 2, 15)));
        assertEquals(HomeResolver.Source.NONE, timeline.sourceOf(timeline.homeFor(LocalDate.of(2026, 2, 15))));
    }

    @Test
    void recentLongStayIsReportedAsPossibleChange() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate stayStart = LocalDate.of(2026, 3, 10);
        LocalDate end = LocalDate.of(2026, 3, 31);
        SortedMap<LocalDate, NightCalculator.Night> nights = nights(start, end, date -> date.isBefore(stayStart) ? HOME : NEW_HOME);

        HomeResolver.HomeTimeline timeline = resolver.resolve(nights, Set.of(), start, end);

        assertEquals(HOME, timeline.homeFor(end));
        assertEquals(NEW_HOME, timeline.changingTo());
    }

    @Test
    void nightCalculatorAssignsPlaceWithMostNightTime() {
        Map<Long, StatisticsData.Place> places = Map.of(
                HOME, place(HOME),
                VACATION, place(VACATION));
        LocalDate day = LocalDate.of(2026, 6, 1);
        List<StatisticsData.Visit> visits = List.of(
                new StatisticsData.Visit(VACATION, at(day, 18, 0), at(day, 23, 0)),
                new StatisticsData.Visit(HOME, at(day, 23, 30), at(day.plusDays(1), 8, 0)));

        SortedMap<LocalDate, NightCalculator.Night> nights = nightCalculator.calculate(visits, List.of(), places, ZONE, day, day);

        assertEquals(NightCalculator.State.AT_PLACE, nights.get(day).state());
        assertEquals(HOME, nights.get(day).placeId());
    }

    @Test
    void nightCalculatorBridgesRecordingGapsAtTheSamePlace() {
        Map<Long, StatisticsData.Place> places = Map.of(HOME, place(HOME));
        LocalDate day = LocalDate.of(2026, 6, 1);
        List<StatisticsData.Visit> visits = List.of(
                new StatisticsData.Visit(HOME, at(day, 18, 0), at(day, 22, 30)),
                new StatisticsData.Visit(HOME, at(day.plusDays(1), 7, 0), at(day.plusDays(1), 9, 0)));

        SortedMap<LocalDate, NightCalculator.Night> nights = nightCalculator.calculate(visits, List.of(), places, ZONE, day, day);

        assertTrue(nights.get(day).isAt(HOME));
        assertTrue(nights.get(day).bridged());
    }

    @Test
    void nightCalculatorDetectsOvernightTravel() {
        Map<Long, StatisticsData.Place> places = Map.of(HOME, place(HOME), VACATION, place(VACATION));
        LocalDate day = LocalDate.of(2026, 6, 1);
        List<StatisticsData.Trip> trips = List.of(new StatisticsData.Trip(1L, at(day, 21, 0), at(day.plusDays(1), 7, 0), HOME, VACATION, 900_000, List.of()));

        SortedMap<LocalDate, NightCalculator.Night> nights = nightCalculator.calculate(List.of(), trips, places, ZONE, day, day);

        assertEquals(NightCalculator.State.IN_TRANSIT, nights.get(day).state());
    }

    private static SortedMap<LocalDate, NightCalculator.Night> nights(LocalDate start, LocalDate end, java.util.function.Function<LocalDate, Long> place) {
        SortedMap<LocalDate, NightCalculator.Night> nights = new TreeMap<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            nights.put(date, new NightCalculator.Night(date, NightCalculator.State.AT_PLACE, place.apply(date), false));
        }
        return nights;
    }

    private static StatisticsData.Place place(long id) {
        return new StatisticsData.Place(id, "Place " + id, null, null, SignificantPlace.PlaceType.OTHER, 53.5, 10.0, ZONE);
    }

    private static Instant at(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(ZONE).toInstant();
    }
}
