package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.Language;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.TimeDisplayMode;
import com.dedicatedcode.reitti.model.TimeMode;
import com.dedicatedcode.reitti.model.UnitSystem;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class TimelineControllerAggregatedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private TestingService testingService;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private User user;

    @BeforeEach
    void setUp() {
        testingService.clearData();
        String username = "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        user = this.userService.createNewUser(username, "Timeline Test User", "password", Role.USER, UnitSystem.METRIC, Language.EN, null, null, null, TimeDisplayMode.DEFAULT, TimeMode.TWENTY_FOUR_HOUR, "#e2e2e2");
    }

    @Test
    void aggregatedTimelineRendersStatsElements() throws Exception {
        ZoneId tz = ZoneId.of("Europe/Berlin");
        LocalDate startDate = LocalDate.of(2025, 2, 3);
        SignificantPlace place = testingService.newSignificantPlace(user, "Test Place");

        for (int day = 0; day < 16; day++) {
            LocalDate date = startDate.plusDays(day);
            Instant dayStart = date.atStartOfDay(tz).toInstant();
            Instant dayEnd = date.atTime(LocalTime.of(23, 59)).atZone(tz).toInstant();

            ProcessedVisit visit1 = testingService.createVisit(user, place, dayStart.plus(5, ChronoUnit.HOURS), dayStart.plus(7, ChronoUnit.HOURS));
            ProcessedVisit visit2 = testingService.createVisit(user, place, dayStart.plus(16, ChronoUnit.HOURS), dayEnd);
            testingService.createTrip(user, visit1, visit2, TransportMode.WALKING);
        }
        jdbcTemplate.update("UPDATE trip_transport_modes SET distance_meters = 5000", Map.of());

        mockMvc.perform(get("/timeline/content/range")
                                .param("startDate", startDate.toString())
                                .param("endDate", startDate.plusDays(15).toString())
                                .param("timezone", tz.toString())
                                .with(user(user)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("stats-kpis")))
                .andExpect(content().string(containsString("timeline-activity-hourly")))
                .andExpect(content().string(containsString("timeline-activity-hour")))
                .andExpect(content().string(containsString("level-4")))
                .andExpect(content().string(containsString("stats-bar-track")))
                .andExpect(content().string(containsString("mode-WALKING")))
                .andExpect(content().string(containsString("timeline-top-places")))
                .andExpect(content().string(containsString("Test Place")))
                .andExpect(content().string(containsString("Most time spent")))
                .andExpect(content().string(containsString("<small>km</small>")))
                .andExpect(content().string(not(containsString("activity-overview-chart"))));
    }

    @Test
    void aggregatedTimelineRendersDayCellsForMonthlyEntries() throws Exception {
        ZoneId tz = ZoneId.of("Europe/Berlin");
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        SignificantPlace place = testingService.newSignificantPlace(user, "Test Place");

        for (int day = 0; day < 10; day++) {
            LocalDate date = startDate.plusDays(day);
            Instant dayStart = date.atStartOfDay(tz).toInstant();
            Instant dayEnd = date.atTime(LocalTime.of(23, 59)).atZone(tz).toInstant();

            ProcessedVisit visit1 = testingService.createVisit(user, place, dayStart.plus(5, ChronoUnit.HOURS), dayStart.plus(7, ChronoUnit.HOURS));
            ProcessedVisit visit2 = testingService.createVisit(user, place, dayStart.plus(16, ChronoUnit.HOURS), dayEnd);
            testingService.createTrip(user, visit1, visit2, TransportMode.CYCLING);
        }

        mockMvc.perform(get("/timeline/content/range")
                                .param("startDate", startDate.toString())
                                .param("endDate", startDate.plusYears(1).toString())
                                .param("timezone", tz.toString())
                                .with(user(user)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("timeline-activity is-month")))
                .andExpect(content().string(containsString("timeline-activity-day")))
                .andExpect(content().string(containsString("level-4")))
                .andExpect(content().string(containsString("mode-CYCLING")))
                .andExpect(content().string(not(containsString("timeline-activity-hourly"))));
    }
}
