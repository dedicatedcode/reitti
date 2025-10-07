package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.memory.*;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class MemoryBlockTripJdbcServiceTest {

    @Autowired
    private MemoryBlockTripJdbcService memoryBlockTripJdbcService;

    @Autowired
    private MemoryBlockJdbcService memoryBlockJdbcService;

    @Autowired
    private MemoryJdbcService memoryJdbcService;

    @Autowired
    private TestingService testingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TripJdbcService tripJdbcService;

    @Autowired
    private ProcessedVisitJdbcService processedVisitJdbcService;

    @Autowired
    private SignificantPlaceJdbcService significantPlaceJdbcService;

    private User testUser;
    private Memory testMemory;
    private MemoryBlock testBlock;
    private Long testTripId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM memory_block_trip");
        jdbcTemplate.update("DELETE FROM memory_block");
        jdbcTemplate.update("DELETE FROM memory");
        jdbcTemplate.update("DELETE FROM trips");
        jdbcTemplate.update("DELETE FROM processed_visits");
        jdbcTemplate.update("DELETE FROM significant_places");

        testUser = testingService.randomUser();

        Memory memory = new Memory(
                "Test Memory",
                "Description",
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 7),
                HeaderType.MAP,
                null
        );

        testMemory = memoryJdbcService.create(testUser, memory);

        MemoryBlock block = new MemoryBlock(testMemory.getId(), BlockType.TRIP, 0);
        testBlock = memoryBlockJdbcService.create(block);


        Instant now = Instant.now();
        SignificantPlace startPlace = this.significantPlaceJdbcService.create(testUser, SignificantPlace.create(60.1699, 24.9384));
        SignificantPlace endPlace = this.significantPlaceJdbcService.create(testUser, SignificantPlace.create(61.1700, 25.9385));

        ProcessedVisit startVisit = this.processedVisitJdbcService.create(testUser, new ProcessedVisit(startPlace, now, now.plus(20, ChronoUnit.MINUTES), 20*60L));
        ProcessedVisit endVisit = this.processedVisitJdbcService.create(testUser, new ProcessedVisit(endPlace, now.plus(3600, ChronoUnit.SECONDS), now.plus(80, ChronoUnit.MINUTES), 20*60L));
        // Create a test tri
        jdbcTemplate.update(
                "INSERT INTO trips (user_id, start_time, end_time, duration_seconds, estimated_distance_meters, travelled_distance_meters, transport_mode_inferred, version, start_visit_id, end_visit_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                testUser.getId(),
                java.sql.Timestamp.from(startVisit.getEndTime()),
                java.sql.Timestamp.from(endVisit.getStartTime()),
                3600L, 5000.0, 5100.0, "WALKING", 1L, startVisit.getId(), endVisit.getId()
        );

        testTripId = jdbcTemplate.queryForObject(
                "SELECT id FROM trips WHERE user_id = ?",
                Long.class,
                testUser.getId()
        );
    }

    @Test
    void testCreateTripBlock() {
        MemoryBlockTrip tripBlock = new MemoryBlockTrip(testBlock.getId(), testTripId);

        MemoryBlockTrip created = memoryBlockTripJdbcService.create(tripBlock);

        assertEquals(testBlock.getId(), created.getBlockId());
        assertEquals(testTripId, created.getTripId());
    }

    @Test
    void testFindByBlockId() {
        MemoryBlockTrip tripBlock = new MemoryBlockTrip(testBlock.getId(), testTripId);
        memoryBlockTripJdbcService.create(tripBlock);

        Optional<MemoryBlockTrip> found = memoryBlockTripJdbcService.findByBlockId(testBlock.getId());

        assertTrue(found.isPresent());
        assertEquals(testBlock.getId(), found.get().getBlockId());
        assertEquals(testTripId, found.get().getTripId());
    }

    @Test
    void testDeleteTripBlock() {
        MemoryBlockTrip tripBlock = new MemoryBlockTrip(testBlock.getId(), testTripId);
        memoryBlockTripJdbcService.create(tripBlock);

        memoryBlockTripJdbcService.delete(testBlock.getId());

        Optional<MemoryBlockTrip> found = memoryBlockTripJdbcService.findByBlockId(testBlock.getId());
        assertFalse(found.isPresent());
    }

    @Test
    void testFindByBlockIdNotFound() {
        Optional<MemoryBlockTrip> found = memoryBlockTripJdbcService.findByBlockId(999L);
        assertFalse(found.isPresent());
    }
}
