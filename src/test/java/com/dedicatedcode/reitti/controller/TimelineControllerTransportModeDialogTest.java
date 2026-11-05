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
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.UserService;
import com.dedicatedcode.reitti.service.processing.TransportModeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class TimelineControllerTransportModeDialogTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private TestingService testingService;

    @Autowired
    private TransportModeService transportModeService;

    private User user;

    @BeforeEach
    void setUp() {
        testingService.clearData();
        String username = "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        user = this.userService.createNewUser(username, "Dialog Test User", "password", Role.USER, UnitSystem.METRIC,
                Language.EN, null, null, null, TimeDisplayMode.DEFAULT, TimeMode.TWENTY_FOUR_HOUR, "#e2e2e2");
    }

    @Test
    void showsDetectedConfidenceAndManualOverride() throws Exception {
        Instant start = Instant.parse("2025-02-03T10:00:00Z");
        Instant end = start.plus(10, ChronoUnit.MINUTES);
        SignificantPlace place = testingService.newSignificantPlace(user, "Test Place");
        ProcessedVisit startVisit = testingService.createVisit(user, place, start.minus(10, ChronoUnit.MINUTES), start);
        ProcessedVisit endVisit = testingService.createVisit(user, place, end, end.plus(10, ChronoUnit.MINUTES));
        Trip trip = testingService.createTrip(user, startVisit, endVisit, TransportMode.WALKING);

        mockMvc.perform(get("/timeline/trips/transport-mode-dialog/{id}", trip.getId()).with(user(user)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("transport-mode-confidence")))
                .andExpect(content().string(containsString("Detected with 100% confidence")));

        transportModeService.overrideTransportModeSegment(user, TransportMode.CYCLING, trip, 0, trip.getDurationSeconds());

        mockMvc.perform(get("/timeline/trips/transport-mode-dialog/{id}", trip.getId()).with(user(user)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("transport-mode-confidence manual")))
                .andExpect(content().string(containsString("Manual")));
    }
}
