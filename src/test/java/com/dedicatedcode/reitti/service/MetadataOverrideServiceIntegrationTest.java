package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.metadata.MemoryMetadata;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ProcessedVisitJdbcService;
import com.dedicatedcode.reitti.repository.TripJdbcService;
import com.dedicatedcode.reitti.service.MetadataOverrideService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
class MetadataOverrideServiceIntegrationTest {

    @Autowired
    private MetadataOverrideService metadataService;

    @Autowired
    private TripJdbcService tripJdbcService;

    @Autowired
    private ProcessedVisitJdbcService processedVisitJdbcService;

    @Autowired
    private TestingService testingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User user;
    private SignificantPlace place;

    @BeforeEach
    void setUp() {
        user = testingService.randomUser();
        place = testingService.newSignificantPlace(user);
    }

    @Test
    void saveTripMetadataCreatesOverrideAndUpdatesTrip() {
        Long visitId = createVisit(place, Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));
        Long tripId = createTrip(visitId, visitId, Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));

        Trip trip = tripJdbcService.findById(tripId).orElseThrow();
        MemoryMetadata metadata = new MemoryMetadata(trip.getStartTime(), trip.getEndTime());
        metadata.setReason("commute");

        metadataService.saveTripMetadata(user, trip, metadata);

        // Check trip metadata was updated
        Trip updatedTrip = tripJdbcService.findById(tripId).orElseThrow();
        assertEquals("commute", updatedTrip.getMetadata().get("reason"));

        // Check override table has a matching row
        Optional<MemoryMetadata> override = metadataService.findOverlappingMetadata(
            user, trip.getStartTime(), trip.getEndTime());
        assertTrue(override.isPresent());
        assertEquals("commute", override.get().getReason());
    }

    @Test
    void saveVisitMetadataCreatesOverrideAndUpdatesVisit() {
        Long visitId = createVisit(place, Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));
        ProcessedVisit visit = processedVisitJdbcService.findById(visitId).orElseThrow();

        MemoryMetadata metadata = new MemoryMetadata(visit.getStartTime(), visit.getEndTime());
        metadata.setDescription("groceries");

        metadataService.saveVisitMetadata(user, visit, metadata);

        ProcessedVisit updatedVisit = processedVisitJdbcService.findById(visitId).orElseThrow();
        assertEquals("groceries", updatedVisit.getMetadata().get("description"));

        Optional<MemoryMetadata> override = metadataService.findOverlappingMetadata(
            user, visit.getStartTime(), visit.getEndTime());
        assertTrue(override.isPresent());
        assertEquals("groceries", override.get().getDescription());
    }

    @Test
    void repeatedSaveReusesExistingOverride() {
        Long visitId = createVisit(place, Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));
        ProcessedVisit visit = processedVisitJdbcService.findById(visitId).orElseThrow();

        MemoryMetadata first = new MemoryMetadata(visit.getStartTime(), visit.getEndTime());
        first.setReason("first");
        metadataService.saveVisitMetadata(user, visit, first);

        MemoryMetadata second = new MemoryMetadata(visit.getStartTime(), visit.getEndTime());
        second.setReason("second");
        metadataService.saveVisitMetadata(user, visit, second);

        // Only one override row should exist for this time range
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM location_metadata WHERE user_id = ?", Integer.class, user.getId());
        assertEquals(1, count);

        // The override should contain the latest reason
        Optional<MemoryMetadata> override = metadataService.findOverlappingMetadata(
            user, visit.getStartTime(), visit.getEndTime());
        assertTrue(override.isPresent());
        assertEquals("second", override.get().getReason());
    }

    // --- helpers ---

    private Long createVisit(SignificantPlace place, Instant start, Instant end) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO processed_visits (user_id, place_id, start_time, end_time, duration_seconds, metadata) VALUES (?,?,?,?,?,?::jsonb) RETURNING id",
                Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, this.user.getId());
            ps.setLong(2, place.getId());
            ps.setTimestamp(3, Timestamp.from(start));
            ps.setTimestamp(4, Timestamp.from(end));
            ps.setLong(5, end.getEpochSecond() - start.getEpochSecond());
            ps.setString(6, "{}");
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private Long createTrip(Long startVisitId, Long endVisitId, Instant start, Instant end) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO trips (user_id, start_time, end_time, duration_seconds, estimated_distance_meters, travelled_distance_meters, transport_mode_inferred, start_visit_id, end_visit_id, metadata) VALUES (?,?,?,?,?,?,?,?,?,?::jsonb) RETURNING id",
                Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, this.user.getId());
            ps.setTimestamp(2, Timestamp.from(start));
            ps.setTimestamp(3, Timestamp.from(end));
            long duration = end.getEpochSecond() - start.getEpochSecond();
            ps.setLong(4, duration);
            ps.setDouble(5, 0.0); // estimated distance
            ps.setDouble(6, 0.0); // travelled distance
            ps.setString(7, TransportMode.UNKNOWN.name());
            ps.setLong(8, startVisitId);
            ps.setLong(9, endVisitId);
            ps.setString(10, "{}");
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }
}