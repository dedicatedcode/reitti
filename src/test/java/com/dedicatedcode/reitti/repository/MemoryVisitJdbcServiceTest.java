package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.memory.BlockType;
import com.dedicatedcode.reitti.model.memory.Memory;
import com.dedicatedcode.reitti.model.memory.MemoryBlock;
import com.dedicatedcode.reitti.model.memory.MemoryTrip;
import com.dedicatedcode.reitti.model.memory.MemoryVisit;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.memory.HeaderType;
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
class MemoryVisitJdbcServiceTest {

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
    private Memory testMemory;
    private MemoryBlock testBlock;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM memory_visits");
        jdbcTemplate.update("DELETE FROM memory_block");
        jdbcTemplate.update("DELETE FROM memory");

        testUser = testingService.randomUser();
        testMemory = memoryJdbcService.create(testUser, new Memory(
                "Test Memory",
                "Description",
                LocalDate.of(2024, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
                LocalDate.of(2024, 1, 7).atStartOfDay().toInstant(ZoneOffset.UTC),
                HeaderType.MAP,
                null));
        testBlock = memoryBlockJdbcService.create(testUser, new MemoryBlock(testMemory.getId(), BlockType.CLUSTER_VISIT, 0));
    }

    @Test
    void save_ShouldPersistVisit() {
        MemoryVisit saved = memoryVisitJdbcService.save(testUser, createVisit("Home", 53.86, 10.70), testBlock.getId(), null);

        List<MemoryVisit> found = memoryVisitJdbcService.findByMemoryBlockId(testUser, testBlock.getId());

        assertEquals(1, found.size());
        assertEquals(saved.getId(), found.getFirst().getId());
        assertEquals("Home", found.getFirst().getName());
    }

    @Test
    void findByMemoryBlockId_WithForeignUser_ShouldReturnEmpty() {
        memoryVisitJdbcService.save(testUser, createVisit("Secret Place", 53.86, 10.70), testBlock.getId(), null);
        User otherUser = testingService.randomUser();

        List<MemoryVisit> found = memoryVisitJdbcService.findByMemoryBlockId(otherUser, testBlock.getId());

        assertTrue(found.isEmpty());
    }

    @Test
    void deleteById_WithForeignUser_ShouldNotDeleteVisit() {
        MemoryVisit saved = memoryVisitJdbcService.save(testUser, createVisit("Keep", 53.86, 10.70), testBlock.getId(), null);
        User otherUser = testingService.randomUser();

        memoryVisitJdbcService.deleteById(otherUser, saved.getId());

        assertEquals(1, memoryVisitJdbcService.findByMemoryBlockId(testUser, testBlock.getId()).size());
    }

    private MemoryVisit createVisit(String name, double latitude, double longitude) {
        return new MemoryVisit(
                null,
                true,
                name,
                Instant.parse("2024-01-02T10:00:00Z"),
                Instant.parse("2024-01-02T11:00:00Z"),
                latitude,
                longitude,
                ZoneId.of("UTC"));
    }
}