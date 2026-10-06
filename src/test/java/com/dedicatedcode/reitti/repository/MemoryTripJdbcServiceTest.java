package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.memory.BlockType;
import com.dedicatedcode.reitti.model.memory.HeaderType;
import com.dedicatedcode.reitti.model.memory.Memory;
import com.dedicatedcode.reitti.model.memory.MemoryBlock;
import com.dedicatedcode.reitti.model.memory.MemoryTrip;
import com.dedicatedcode.reitti.model.memory.MemoryVisit;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class MemoryTripJdbcServiceTest {

    @Autowired
    private MemoryTripJdbcService memoryTripJdbcService;

    @Autowired
    private MemoryVisitJdbcService memoryVisitJdbcService;

    @Autowired
    private MemoryJdbcService memoryJdbcService;

    @Autowired
    private MemoryBlockJdbcService memoryBlockJdbcService;

    @Autowired
    private TestingService testingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User testUser;
    private MemoryBlock testBlock;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM memory_trips");
        jdbcTemplate.update("DELETE FROM memory_visits");
        jdbcTemplate.update("DELETE FROM memory_block");
        jdbcTemplate.update("DELETE FROM memory");

        testUser = testingService.randomUser();
        Memory memory = memoryJdbcService.create(testUser, new Memory(
                "Test Memory",
                "Description",
                LocalDate.of(2024, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
                LocalDate.of(2024, 1, 7).atStartOfDay().toInstant(ZoneOffset.UTC),
                HeaderType.MAP,
                null));
        testBlock = memoryBlockJdbcService.create(testUser, new MemoryBlock(memory.getId(), BlockType.CLUSTER_TRIP, 0));
    }

    @Test
    void save_ShouldPersistTrip() {
        MemoryTrip saved = createTrip("Work", "Gym");

        List<MemoryTrip> found = memoryTripJdbcService.findByMemoryBlockId(testUser, testBlock.getId());

        assertEquals(1, found.size());
        assertEquals(saved.getId(), found.getFirst().getId());
        assertEquals("Work", found.getFirst().getStartVisit().getName());
        assertEquals("Gym", found.getFirst().getEndVisit().getName());
    }

    @Test
    void findByMemoryBlockId_WithForeignUser_ShouldReturnEmpty() {
        createTrip("Secret Origin", "Secret Destination");
        User otherUser = testingService.randomUser();

        List<MemoryTrip> found = memoryTripJdbcService.findByMemoryBlockId(otherUser, testBlock.getId());

        assertTrue(found.isEmpty());
    }

    @Test
    void deleteById_WithForeignUser_ShouldNotDeleteTrip() {
        MemoryTrip saved = createTrip("Keep", "Keep");
        User otherUser = testingService.randomUser();

        memoryTripJdbcService.deleteById(otherUser, saved.getId());

        assertEquals(1, memoryTripJdbcService.findByMemoryBlockId(testUser, testBlock.getId()).size());
    }

    private MemoryTrip createTrip(String startName, String endName) {
        MemoryVisit startVisit = memoryVisitJdbcService.save(testUser, createVisit(startName), testBlock.getId(), null);
        MemoryVisit endVisit = memoryVisitJdbcService.save(testUser, createVisit(endName), testBlock.getId(), null);
        MemoryTrip trip = new MemoryTrip(null, true, startVisit, endVisit,
                Instant.parse("2024-01-02T11:00:00Z"), Instant.parse("2024-01-02T12:00:00Z"));
        return memoryTripJdbcService.save(testUser, trip, testBlock.getId(), null);
    }

    private MemoryVisit createVisit(String name) {
        return new MemoryVisit(
                null,
                true,
                name,
                Instant.parse("2024-01-02T10:00:00Z"),
                Instant.parse("2024-01-02T11:00:00Z"),
                53.86,
                10.70,
                ZoneId.of("UTC"));
    }
}