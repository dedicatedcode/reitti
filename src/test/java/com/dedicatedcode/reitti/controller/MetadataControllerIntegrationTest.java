package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.metadata.MemoryMetadata;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.MetadataOverrideJdbcService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@IntegrationTest
class MetadataControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestingService testingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MetadataOverrideJdbcService overrideJdbcService;

    private User user;
    private Long tripId;
    private Long visitId;

    @BeforeEach
    void setUp() {
        user = testingService.randomUser();
        authenticate(user);

        SignificantPlace place = testingService.newSignificantPlace(user);

        Instant start = Instant.parse("2025-01-01T10:00:00Z");
        Instant end = Instant.parse("2025-01-01T11:00:00Z");

        // Create a visit
        visitId = createVisit(user, place, start, end);

        // Create a trip referencing that visit as both start and end (simplified)
        tripId = createTrip(user, visitId, visitId, start, end);
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
    void getTripMetadataForm() throws Exception {
        mockMvc.perform(get("/metadata/trip/{id}", tripId)
                        .param("timezone", "UTC"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/index/metadata :: metadata"))
                .andExpect(model().attributeExists("metadata", "availableMoods", "timerange"));
    }

    @Test
    void getVisitMetadataForm() throws Exception {
        mockMvc.perform(get("/metadata/visit/{id}", visitId)
                        .param("timezone", "UTC"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/index/metadata :: metadata"))
                .andExpect(model().attributeExists("metadata", "availableMoods", "timerange"));
    }

    @Test
    void updateMetadataForTripAndRedirect() throws Exception {
        mockMvc.perform(post("/metadata")
                        .param("type", "trip")
                        .param("id", tripId.toString())
                        .param("mood", "HAPPY")
                        .param("reason", "commute")
                        .param("notes", "took train")
                        .param("tags", "transit")
                        .param("returnUrl", "/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        // Verify metadata persisted on trip
        String reason = jdbcTemplate.queryForObject(
                "SELECT metadata->>'reason' FROM trips WHERE id = ?", String.class, tripId);
        assertEquals("commute", reason);
    }

    @Test
    void getReasonSuggestions() throws Exception {
        // Seed some metadata overrides
        Instant start = Instant.parse("2025-01-01T10:00:00Z");
        Instant end = Instant.parse("2025-01-01T11:00:00Z");
        MemoryMetadata m1 = new MemoryMetadata(start, end);
        m1.setReason("groceries");
        overrideJdbcService.insertOverride(user, "TRIP", m1);

        MemoryMetadata m2 = new MemoryMetadata(start, end);
        m2.setReason("grocery shopping");
        overrideJdbcService.insertOverride(user, "TRIP", m2);

        mockMvc.perform(get("/metadata/suggestions/reason")
                        .param("query", "groc"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$", containsInAnyOrder("groceries", "grocery shopping")));
    }

    @Test
    void getTagSuggestions() throws Exception {
        Instant start = Instant.parse("2025-01-01T10:00:00Z");
        Instant end = Instant.parse("2025-01-01T11:00:00Z");
        MemoryMetadata m = new MemoryMetadata(start, end);
        m.setTags(List.of("java", "spring"));
        overrideJdbcService.insertOverride(user, "TRIP", m);

        mockMvc.perform(get("/metadata/suggestions/tags")
                        .param("query", "jav"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", contains("java")));
    }

    private Long createVisit(User user, SignificantPlace place, Instant start, Instant end) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO processed_visits (user_id, place_id, start_time, end_time, duration_seconds, metadata) VALUES (?,?,?,?,?,?::jsonb) RETURNING id",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, user.getId());
            ps.setLong(2, place.getId());
            ps.setTimestamp(3, Timestamp.from(start));
            ps.setTimestamp(4, Timestamp.from(end));
            ps.setLong(5, end.getEpochSecond() - start.getEpochSecond());
            ps.setString(6, "{}");
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private Long createTrip(User user, Long startVisit, Long endVisit, Instant start, Instant end) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO trips (user_id, start_time, end_time, duration_seconds, estimated_distance_meters, travelled_distance_meters, transport_mode_inferred, start_visit_id, end_visit_id, metadata) VALUES (?,?,?,?,?,?,?,?,?,?::jsonb) RETURNING id",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, user.getId());
            ps.setTimestamp(2, Timestamp.from(start));
            ps.setTimestamp(3, Timestamp.from(end));
            long duration = end.getEpochSecond() - start.getEpochSecond();
            ps.setLong(4, duration);
            ps.setDouble(5, 0.0);
            ps.setDouble(6, 0.0);
            ps.setString(7, TransportMode.UNKNOWN.name());
            ps.setLong(8, startVisit);
            ps.setLong(9, endVisit);
            ps.setString(10, "{}");
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }
}