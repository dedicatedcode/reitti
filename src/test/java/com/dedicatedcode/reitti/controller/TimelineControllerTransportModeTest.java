package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class TimelineControllerTransportModeTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestingService testingService;
    @Autowired
    private TripJdbcService tripJdbcService;

    private User owner;
    private Trip trip;

    @BeforeEach
    void setUp() {
        owner = testingService.randomUser();
        SignificantPlace place = testingService.newSignificantPlace(owner);
        ProcessedVisit start = testingService.createVisit(owner, place, Instant.parse("2025-01-01T10:00:00Z"), Instant.parse("2025-01-01T11:00:00Z"));
        ProcessedVisit end = testingService.createVisit(owner, place, Instant.parse("2025-01-01T12:00:00Z"), Instant.parse("2025-01-01T13:00:00Z"));
        trip = testingService.createTrip(owner, start, end, TransportMode.WALKING);
    }

    @AfterEach
    void tearDown() {
        testingService.clearData();
    }

    @Test
    void ownerCanChangeTransportModes() throws Exception {
        mockMvc.perform(post("/timeline/trips/{id}/transport-modes", trip.getId())
                                .param("segments[0].offsetSeconds", "0")
                                .param("segments[0].transportMode", "CYCLING")
                                .param("returnUrl", "/?startDate=2025-01-01")
                                .with(user(owner)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?startDate=2025-01-01"));

        assertEquals(TransportMode.CYCLING, tripJdbcService.findById(trip.getId()).orElseThrow().getSegments().getFirst().mode());
    }

    @Test
    void otherUsersCanNotReadOrChangeTransportModes() throws Exception {
        User intruder = testingService.randomUser();

        mockMvc.perform(get("/timeline/trips/transport-mode-dialog/{id}", trip.getId()).with(user(intruder)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/timeline/trips/{id}/transport-modes", trip.getId())
                                .param("segments[0].offsetSeconds", "0")
                                .param("segments[0].transportMode", "CYCLING")
                                .with(user(intruder)))
                .andExpect(status().isNotFound());

        assertEquals(TransportMode.WALKING, tripJdbcService.findById(trip.getId()).orElseThrow().getSegments().getFirst().mode());
    }

    @Test
    void transportModeUpdateDoesNotRedirectToOtherSites() throws Exception {
        mockMvc.perform(post("/timeline/trips/{id}/transport-modes", trip.getId())
                                .param("segments[0].offsetSeconds", "0")
                                .param("segments[0].transportMode", "WALKING")
                                .param("returnUrl", "https://evil.example/")
                                .with(user(owner)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }
}
