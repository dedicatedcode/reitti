package com.dedicatedcode.reitti.service.statistics;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StatisticsReportBuilderTest {

    private static final Long HOME = 1L;
    private static final Long FAR = 2L;
    private static final Long NEAR = 3L;
    private static final int MIN_NIGHTS = 3;
    private static final double MIN_DISTANCE_METERS = 10_000;

    @Test
    void includesConsecutiveFarAwayRun() {
        assertThat(qualifying(run(LocalDate.of(2025, 1, 1), 3, FAR))).hasSize(3);
    }

    @Test
    void excludesRunShorterThanMinimum() {
        assertThat(qualifying(run(LocalDate.of(2025, 1, 1), 2, FAR))).isEmpty();
    }

    @Test
    void excludesRunCloserThanMinimumDistance() {
        assertThat(qualifying(run(LocalDate.of(2025, 1, 1), 4, NEAR))).isEmpty();
    }

    @Test
    void qualifiesWhenAnyNightOfTheRunIsFarEnough() {
        List<NightCalculator.Night> nights = new ArrayList<>();
        nights.add(night(LocalDate.of(2025, 1, 1), NEAR));
        nights.add(night(LocalDate.of(2025, 1, 2), FAR));
        nights.add(night(LocalDate.of(2025, 1, 3), NEAR));

        assertThat(qualifying(nights)).hasSize(3);
    }

    @Test
    void treatsGapsAsSeparateRuns() {
        List<NightCalculator.Night> nights = new ArrayList<>();
        nights.addAll(run(LocalDate.of(2025, 1, 1), 2, FAR));
        nights.addAll(run(LocalDate.of(2025, 1, 10), 2, FAR));

        assertThat(qualifying(nights)).isEmpty();
    }

    @Test
    void ignoresTransitNightsForTheDistance() {
        List<NightCalculator.Night> nights = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            nights.add(new NightCalculator.Night(LocalDate.of(2025, 1, 1).plusDays(i), NightCalculator.State.IN_TRANSIT, null, false));
        }

        assertThat(qualifying(nights)).isEmpty();
    }

    private static List<NightCalculator.Night> qualifying(List<NightCalculator.Night> nights) {
        return StatisticsReportBuilder.qualifyingAwayRuns(nights, night -> HOME,
                                                          (home, place) -> Optional.of(place.equals(FAR) ? 500_000.0 : 3_000.0),
                                                          MIN_NIGHTS, MIN_DISTANCE_METERS);
    }

    private static List<NightCalculator.Night> run(LocalDate start, int nights, Long placeId) {
        List<NightCalculator.Night> result = new ArrayList<>();
        for (int i = 0; i < nights; i++) {
            result.add(night(start.plusDays(i), placeId));
        }
        return result;
    }

    private static NightCalculator.Night night(LocalDate date, Long placeId) {
        return new NightCalculator.Night(date, NightCalculator.State.AT_PLACE, placeId, false);
    }
}
