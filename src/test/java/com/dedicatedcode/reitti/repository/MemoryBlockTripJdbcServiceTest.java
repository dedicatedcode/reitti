package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.memory.*;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
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

    private User testUser;
    private Memory testMemory;
    private MemoryBlock testBlock;
    private Long testTripId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM memory_block_trip");
        jdbcTemplate.update("DELETE FROM memory_block");
        jdbcTemplate.update("DELETE FROM memory");
        jdbcTemplate.update("DELETE FROM trip");

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

        // Create a test trip
        jdbcTemplate.update(
                "INSERT INTO trip (user_id, start_time, end_time, duration_seconds, estimated_distance_meters, travelled_distance_meters, transport_mode_inferred, version) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                testUser.getId(),
                java.sql.Timestamp.from(java.time.Instant.now()),
                java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(3600)),
                3600L, 5000.0, 5100.0, "WALKING", 1L
        );

        testTripId = jdbcTemplate.queryForObject(
                "SELECT id FROM trip WHERE user_id = ?",
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
