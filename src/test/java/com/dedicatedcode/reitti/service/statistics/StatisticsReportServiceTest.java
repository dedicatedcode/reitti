package com.dedicatedcode.reitti.service.statistics;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.TimeMode;
import com.dedicatedcode.reitti.model.UnitSystem;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.SignificantPlaceJdbcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.*;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class StatisticsReportServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final LocalDate VACATION_START = LocalDate.of(2025, 9, 10);
    private static final LocalDate VACATION_END = LocalDate.of(2025, 9, 16);

    @Autowired
    private StatisticsReportService reportService;
    @Autowired
    private TestingService testingService;
    @Autowired
    private SignificantPlaceJdbcService placeJdbcService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MockMvc mockMvc;

    private User user;
    private SignificantPlace home;
    private SignificantPlace office;
    private SignificantPlace lakeHouse;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        user = testingService.randomUser();
        home = place(52.5200, 13.4050, "Home Street", "Berlin");
        office = place(52.5000, 13.4500, "Office", "Berlin");
        SignificantPlace cafe = place(52.5300, 13.3800, "Cafe", "Berlin");
        lakeHouse = place(48.1351, 11.5820, "Lake House", "Munich");

        for (LocalDate date = LocalDate.of(2025, 8, 1); !date.isAfter(LocalDate.of(2025, 9, 30)); date = date.plusDays(1)) {
            if (!date.isBefore(VACATION_START) && !date.isAfter(VACATION_END)) {
                visit(lakeHouse, date, 0, 0, date.plusDays(1), 0, 0);
                continue;
            }
            boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
            if (weekend) {
                ProcessedVisit morning = visit(home, date, 0, 0, date, 10, 0);
                ProcessedVisit coffee = visit(cafe, date, 11, 0, date, 12, 0);
                ProcessedVisit evening = visit(home, date, 13, 0, date.plusDays(1), 0, 0);
                trip(morning, coffee, TransportMode.WALKING, 3000);
                trip(coffee, evening, TransportMode.WALKING, 3000);
            } else {
                ProcessedVisit morning = visit(home, date, 0, 0, date, 8, 0);
                ProcessedVisit work = visit(office, date, 8, 30, date, 17, 0);
                ProcessedVisit evening = visit(home, date, 17, 30, date.plusDays(1), 0, 0);
                trip(morning, work, TransportMode.CYCLING, 5000);
                trip(work, evening, TransportMode.CYCLING, 5000);
            }
        }
    }

    @Test
    void buildsMonthReportWithInferredHomeAndVacation() {
        StatisticsReport report = reportService.getReport(user, StatisticsPeriod.ofMonth(2025, 9), ZONE, Locale.ENGLISH, UnitSystem.METRIC, TimeMode.TWENTY_FOUR_HOUR);

        assertThat(report.hasData()).isTrue();
        assertThat(report.title()).isEqualTo("September 2025");
        assertThat(report.home().source()).isEqualTo(HomeResolver.Source.INFERRED);
        assertThat(report.home().name()).isEqualTo("Home Street");
        assertThat(report.home().suggestion()).isNotNull();
        assertThat(report.home().suggestion().placeId()).isEqualTo(home.getId());
        assertThat(report.home().changingText()).isNull();
        assertThat(report.trackingLine()).startsWith("30 of 30 days tracked");
        assertThat(report.kpis()).anySatisfy(kpi -> {
            assertThat(kpi.label()).isEqualTo("nights away");
            assertThat(kpi.value()).isEqualTo("7");
        });
        assertThat(report.usualTrip()).isNotNull();
        assertThat(report.usualTrip().title()).isEqualTo("Home Street → Office");
        assertThat(report.stays()).anySatisfy(line -> assertThat(line).contains("Munich, 7 nights"));
        assertThat(report.stays()).anySatisfy(line -> assertThat(line).startsWith("7 nights away, longest stretch 7 nights"));
        assertThat(report.stays()).noneSatisfy(line -> assertThat(line).contains("second base"));
        assertThat(report.routines()).noneSatisfy(line -> assertThat(line.lead()).isNotNull().extracting(StatisticsReport.LineLink::label).isEqualTo("Lake House"));
        assertThat(report.routines()).anySatisfy(line -> {
            assertThat(line.lead().label()).isEqualTo("Office");
            assertThat(line.lead().href()).isEqualTo("/settings/places/" + office.getId() + "/edit?returnUrl=/statistics/2025/9");
        });
        assertThat(report.leftovers()).anySatisfy(line -> {
            assertThat(line.text()).isEqualTo("7 nights away never became a memory:");
            assertThat(line.links()).extracting(StatisticsReport.LineLink::href).containsExactly("/?startDate=2025-09-09&endDate=2025-09-16");
            assertThat(line.links()).extracting(StatisticsReport.LineLink::label).containsExactly("9 Sep – 16 Sep");
        });
        assertThat(report.movement()).isNotNull();
        assertThat(report.movement().legend()).extracting(StatisticsReport.Legend::mode).containsExactly("CYCLING", "WALKING");
        assertThat(report.dayTypes().calendar()).hasSize(30);
        assertThat(report.comparison()).isNotNull();
        assertThat(report.comparePeriod()).isEqualTo(StatisticsPeriod.ofMonth(2025, 8));
        assertThat(report.comparison().headline()).isEqualTo("September 2025 compared with August 2025: 30 tracked days, against 31.");
        assertThat(report.comparison().lines()).contains("206 km travelled, against 270 km.");
        assertThat(report.comparison().suggestedOptions()).extracting(StatisticsReport.CompareOption::label)
                .containsExactly("Previous month (August 2025)", "Same month last year (September 2024)");
        assertThat(report.comparison().options()).filteredOn(StatisticsReport.CompareOption::selected)
                .extracting(StatisticsReport.CompareOption::key).containsExactly("2025-8");
        assertThat(report.navigation().previous().key()).isEqualTo("2025-8");
        assertThat(report.navigation().next()).isNull();
        assertThat(report.headline()).isNotBlank();
    }

    @Test
    void markedHomeReplacesInference() {
        placeJdbcService.update(user, home.withType(SignificantPlace.PlaceType.HOME));

        StatisticsReport report = reportService.buildReport(user, StatisticsPeriod.ofMonth(2025, 9), ZONE, UnitSystem.METRIC, TimeMode.TWENTY_FOUR_HOUR, Locale.ENGLISH);

        assertThat(report.home().source()).isEqualTo(HomeResolver.Source.MARKED);
        assertThat(report.home().suggestion()).isNull();
    }

    @Test
    void comparesWithASelectedPeriod() {
        StatisticsReport noData = reportService.getReport(user, StatisticsPeriod.ofMonth(2025, 9), StatisticsPeriod.ofMonth(2024, 9), ZONE,
                                                          Locale.ENGLISH, UnitSystem.METRIC, TimeMode.TWENTY_FOUR_HOUR);
        assertThat(noData.comparison().headline()).isEqualTo("There are no tracked days in September 2024 to compare September 2025 with.");
        assertThat(noData.comparison().lines()).isEmpty();

        for (LocalDate date = LocalDate.of(2023, 3, 1); !date.isAfter(LocalDate.of(2023, 3, 31)); date = date.plusDays(1)) {
            visit(home, date, 0, 0, date.plusDays(1), 0, 0);
        }
        StatisticsReport farAway = reportService.getReport(user, StatisticsPeriod.ofMonth(2025, 9), StatisticsPeriod.ofMonth(2023, 3), ZONE,
                                                           Locale.ENGLISH, UnitSystem.METRIC, TimeMode.TWENTY_FOUR_HOUR);
        assertThat(farAway.comparison().headline()).isEqualTo("September 2025 compared with March 2023: 30 tracked days, against 31.");
        assertThat(farAway.comparison().lines()).contains("206 km travelled, against 0 km.");
        assertThat(farAway.comparison().options()).extracting(StatisticsReport.CompareOption::key).contains("2023-3", "2025-8", "2024-9");
        assertThat(farAway.comparison().options()).extracting(StatisticsReport.CompareOption::key).doesNotContain("2025-9");

        assertThat(reportService.resolveComparison(StatisticsPeriod.ofMonth(2025, 9), "2025")).isEqualTo(StatisticsPeriod.ofMonth(2025, 8));
        assertThat(reportService.resolveComparison(StatisticsPeriod.ofYear(2025), "2023")).isEqualTo(StatisticsPeriod.ofYear(2023));
        assertThat(reportService.resolveComparison(StatisticsPeriod.all(), "2023")).isNull();
    }

    @Test
    void selectingAComparisonPushesTheUrl() throws Exception {
        mockMvc.perform(get("/statistics/fragments/report").with(user(user))
                                .header("HX-Request", "true")
                                .header("HX-Trigger", "stats-compare-select")
                                .param("period", "2025-9")
                                .param("compare", "2024-9")
                                .param("timezone", ZONE.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Push-Url", "/statistics/2025/9?compare=2024-9"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("no tracked days in September 2024")));

        mockMvc.perform(get("/statistics/fragments/report").with(user(user))
                                .header("HX-Request", "true")
                                .header("HX-Trigger", "stats-compare-select")
                                .param("period", "2025-9")
                                .param("compare", "2025-8")
                                .param("timezone", ZONE.getId()))
                .andExpect(header().string("HX-Push-Url", "/statistics/2025/9"));

        mockMvc.perform(get("/statistics/2025/9").param("compare", "2024-9").with(user(user)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("compare=2024-9")));
    }

    @Test
    void laterMarkedHomeDoesNotRewriteThePast() {
        SignificantPlace moltkestrasse = place(53.5511, 9.9937, "Moltkestraße", "Hamburg");
        placeJdbcService.update(user, moltkestrasse.withType(SignificantPlace.PlaceType.HOME));
        for (LocalDate date = LocalDate.of(2026, 3, 1); !date.isAfter(LocalDate.of(2026, 5, 31)); date = date.plusDays(1)) {
            visit(moltkestrasse, date, 0, 0, date.plusDays(1), 0, 0);
        }

        StatisticsReport past = reportService.buildReport(user, StatisticsPeriod.ofMonth(2025, 9), ZONE, UnitSystem.METRIC, TimeMode.TWENTY_FOUR_HOUR, Locale.ENGLISH);
        StatisticsReport now = reportService.buildReport(user, StatisticsPeriod.ofMonth(2026, 4), ZONE, UnitSystem.METRIC, TimeMode.TWENTY_FOUR_HOUR, Locale.ENGLISH);

        assertThat(past.home().name()).isEqualTo("Home Street");
        assertThat(past.home().source()).isEqualTo(HomeResolver.Source.INFERRED);
        assertThat(past.home().suggestion()).isNull();
        assertThat(now.home().name()).isEqualTo("Moltkestraße");
        assertThat(now.home().source()).isEqualTo(HomeResolver.Source.MARKED);
    }

    @Test
    void yearAndAllPeriodsAreAggregated() {
        StatisticsReport year = reportService.buildReport(user, StatisticsPeriod.ofYear(2025), ZONE, UnitSystem.METRIC, TimeMode.TWENTY_FOUR_HOUR, Locale.ENGLISH);
        StatisticsReport all = reportService.buildReport(user, StatisticsPeriod.all(), ZONE, UnitSystem.IMPERIAL, TimeMode.TWELVE_HOUR, Locale.ENGLISH);

        assertThat(year.hasData()).isTrue();
        assertThat(year.title()).isEqualTo("2025");
        assertThat(year.dayTypes().calendar()).hasSize(153);
        assertThat(year.trackingLine()).startsWith("61 of 153 days tracked");
        assertThat(all.hasData()).isTrue();
        assertThat(all.dayTypes().calendar()).isEmpty();
        assertThat(all.kpis().getFirst().unit()).isEqualTo("mi");
    }

    @Test
    void emptyPeriodHasNoData() {
        StatisticsReport report = reportService.buildReport(user, StatisticsPeriod.ofMonth(2024, 1), ZONE, UnitSystem.METRIC, TimeMode.TWENTY_FOUR_HOUR, Locale.ENGLISH);

        assertThat(report.hasData()).isFalse();
        assertThat(report.navigation().next().key()).isEqualTo("2025-8");
    }

    @Test
    void rendersPagesAndFragments() throws Exception {
        mockMvc.perform(get("/statistics/2025/9").with(user(user)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("statistics-root")));

        for (String period : List.of("2025-9", "2025", "all", "")) {
            mockMvc.perform(get("/statistics/fragments/report").with(user(user))
                                    .param("period", period)
                                    .param("timezone", ZONE.getId()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("Home Street")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("stats-explain-panel")));
        }

        mockMvc.perform(get("/statistics/fragments/coverage").with(user(user))
                                .param("period", "2025-9")
                                .param("timezone", ZONE.getId()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/statistics/fragments/photos").with(user(user))
                                .param("period", "2025-9")
                                .param("timezone", ZONE.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void acceptingTheSuggestionMarksThePlaceAsHome() throws Exception {
        mockMvc.perform(post("/statistics/home-suggestion/{id}/accept", home.getId()).with(user(user)).with(csrf())
                                .param("period", "2025-9")
                                .param("timezone", ZONE.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("the place marked as Home")));

        assertThat(placeJdbcService.findById(home.getId()).orElseThrow().getType()).isEqualTo(SignificantPlace.PlaceType.HOME);
    }

    @Test
    void dismissingTheSuggestionHidesIt() throws Exception {
        mockMvc.perform(post("/statistics/home-suggestion/{id}/dismiss", home.getId()).with(user(user)).with(csrf())
                                .param("period", "2025-9")
                                .param("timezone", ZONE.getId()))
                .andExpect(status().isOk());

        StatisticsReport report = reportService.getReport(user, StatisticsPeriod.ofMonth(2025, 9), ZONE, Locale.ENGLISH, UnitSystem.METRIC, TimeMode.TWENTY_FOUR_HOUR);
        assertThat(report.home().suggestion()).isNull();
    }

    private SignificantPlace place(double latitude, double longitude, String name, String city) {
        SignificantPlace place = testingService.newSignificantPlace(user, latitude, longitude, name);
        return placeJdbcService.update(user, place.withCity(city).withTimezone(ZONE));
    }

    private ProcessedVisit visit(SignificantPlace place, LocalDate startDate, int startHour, int startMinute, LocalDate endDate, int endHour, int endMinute) {
        return testingService.createVisit(user, place,
                                          startDate.atTime(startHour, startMinute).atZone(ZONE).toInstant(),
                                          endDate.atTime(endHour, endMinute).atZone(ZONE).toInstant().minusSeconds(1));
    }

    private void trip(ProcessedVisit from, ProcessedVisit to, TransportMode mode, double distanceMeters) {
        Instant start = from.getEndTime();
        Instant end = to.getStartTime();
        long duration = Duration.between(start, end).getSeconds();
        Long tripId = jdbcTemplate.queryForObject("""
                                                  INSERT INTO trips (user_id, start_time, end_time, duration_seconds, estimated_distance_meters, travelled_distance_meters, start_visit_id, end_visit_id, metadata)
                                                  VALUES (?, ?, ?, ?, ?, ?, ?, ?, '{}'::jsonb) RETURNING id
                                                  """, Long.class,
                                                  user.getId(), Timestamp.from(start), Timestamp.from(end), duration, distanceMeters, distanceMeters, from.getId(), to.getId());
        jdbcTemplate.update("INSERT INTO trip_transport_modes (trip_id, offset_seconds, duration_in_seconds, transportation_mode, distance_meters) VALUES (?,?,?,?,?)",
                            tripId, 0L, duration, mode.name(), distanceMeters);
    }
}
