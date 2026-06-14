package com.dedicatedcode.reitti.controller.api.v2;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
class MetadataApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestingService testingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User user;
    private ProcessedVisit visit1;
    private ProcessedVisit visit2;
    private Trip trip;

    @BeforeEach
    void setUp() {
        user = testingService.randomUser();
        authenticate(user);

        SignificantPlace place = testingService.newSignificantPlace(user);

        visit1 = testingService.createVisit(user, place, Instant.parse("2025-01-01T10:00:00Z"), Instant.parse("2025-01-01T11:00:00Z"));
        visit2 = testingService.createVisit(user, place, Instant.parse("2025-01-01T14:00:00Z"), Instant.parse("2025-01-01T16:00:00Z"));
        trip = testingService.createTrip(user, visit1, visit2);
    }

    @AfterEach
    void tearDown() {
        testingService.clearData();
        jdbcTemplate.update("DELETE FROM location_metadata");
        SecurityContextHolder.clearContext();
    }

    private void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @Test
    void getMetadataWhenNoneExistsReturnsNull() throws Exception {
        mockMvc.perform(get("/api/v2/metadata/trip/{id}", trip.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void postMetadataForTripCreatesAndReturnsJson() throws Exception {
        mockMvc.perform(post("/api/v2/metadata/trip/{id}", trip.getId())
                                .param("mood", "HAPPY")
                                .param("reason", "commute")
                                .param("notes", "nice trip")
                                .param("tags", "train", "weekday"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mood").value("HAPPY"))
                .andExpect(jsonPath("$.reason").value("commute"))
                .andExpect(jsonPath("$.description").value("nice trip"))
                .andExpect(jsonPath("$.tags[0]").value("train"))
                .andExpect(jsonPath("$.tags[1]").value("weekday"));

        // Verify trip table updated
        String reason = jdbcTemplate.queryForObject(
                "SELECT metadata->>'reason' FROM trips WHERE id = ?", String.class, trip.getId());
        assertEquals("commute", reason);
    }

    @Test
    void postMetadataForVisitCreatesAndReturnsJson() throws Exception {
        mockMvc.perform(post("/api/v2/metadata/visit/{id}", visit1.getId())
                                .param("reason", "groceries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("groceries"));
    }

    @Test
    void subsequentPostOverwrites() throws Exception {
        // First post
        mockMvc.perform(post("/api/v2/metadata/trip/{id}", trip.getId())
                                .param("reason", "first"))
                .andExpect(status().isOk());

        // Second post with different reason
        mockMvc.perform(post("/api/v2/metadata/trip/{id}", trip.getId())
                                .param("reason", "second"))
                .andExpect(status().isOk());

        // Check that trip metadata has been updated
        String reason = jdbcTemplate.queryForObject(
                "SELECT metadata->>'reason' FROM trips WHERE id = ?", String.class, trip.getId());
        assertEquals("second", reason);

        // Only one override row should exist
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM location_metadata WHERE user_id = ?", Integer.class, user.getId());
        assertEquals(1, count);
    }

}