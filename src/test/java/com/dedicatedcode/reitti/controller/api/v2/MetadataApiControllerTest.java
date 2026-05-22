package com.dedicatedcode.reitti.controller.api.v2;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
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
    private Long tripId;
    private Long visitId;

    @BeforeEach
    void setUp() {
        user = testingService.randomUser();
        authenticate(user);

        SignificantPlace place = testingService.newSignificantPlace(user);
        Instant start = Instant.parse("2025-01-01T10:00:00Z");
        Instant end = Instant.parse("2025-01-01T11:00:00Z");

        visitId = createVisit(user, place.getId(), start, end);
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
    void getMetadataWhenNoneExistsReturnsNull() throws Exception {
        mockMvc.perform(get("/api/v2/metadata/trip/{id}", tripId))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void postMetadataForTripCreatesAndReturnsJson() throws Exception {
        mockMvc.perform(post("/api/v2/metadata/trip/{id}", tripId)
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
                "SELECT metadata->>'reason' FROM trips WHERE id = ?", String.class, tripId);
        assertEquals("commute", reason);
    }

    @Test
    void postMetadataForVisitCreatesAndReturnsJson() throws Exception {
        mockMvc.perform(post("/api/v2/metadata/visit/{id}", visitId)
                                .param("reason", "groceries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("groceries"));
    }

    @Test
    void subsequentPostOverwrites() throws Exception {
        // First post
        mockMvc.perform(post("/api/v2/metadata/trip/{id}", tripId)
                                .param("reason", "first"))
                .andExpect(status().isOk());

        // Second post with different reason
        mockMvc.perform(post("/api/v2/metadata/trip/{id}", tripId)
                                .param("reason", "second"))
                .andExpect(status().isOk());

        // Check that trip metadata has been updated
        String reason = jdbcTemplate.queryForObject(
                "SELECT metadata->>'reason' FROM trips WHERE id = ?", String.class, tripId);
        assertEquals("second", reason);

        // Only one override row should exist
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM location_metadata WHERE user_id = ?", Integer.class, user.getId());
        assertEquals(1, count);
    }

    private Long createVisit(User user, Long placeId, Instant start, Instant end) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO processed_visits (user_id, place_id, start_time, end_time, duration_seconds, metadata) VALUES (?,?,?,?,?,?::jsonb) RETURNING id",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, user.getId());
            ps.setLong(2, placeId);
            ps.setTimestamp(3, Timestamp.from(start));
            ps.setTimestamp(4, Timestamp.from(end));
            ps.setLong(5, end.getEpochSecond() - start.getEpochSecond());
            ps.setString(6, "{}");
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private Long createTrip(User user, Long startVisitId, Long endVisitId, Instant start, Instant end) {
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
            ps.setLong(8, startVisitId);
            ps.setLong(9, endVisitId);
            ps.setString(10, "{}");
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }
}