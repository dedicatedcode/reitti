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
class MemoryBlockVisitJdbcServiceTest {

    @Autowired
    private MemoryBlockVisitJdbcService memoryBlockVisitJdbcService;

    @Autowired
    private MemoryBlockJdbcService memoryBlockJdbcService;

    @Autowired
    private MemoryJdbcService memoryJdbcService;

    @Autowired
    private VisitJdbcService visitJdbcService;

    @Autowired
    private TestingService testingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User testUser;
    private Memory testMemory;
    private MemoryBlock testBlock;
    private Long testVisitId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM memory_block_visit");
        jdbcTemplate.update("DELETE FROM memory_block");
        jdbcTemplate.update("DELETE FROM memory");
        jdbcTemplate.update("DELETE FROM visits");

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

        MemoryBlock block = new MemoryBlock(testMemory.getId(), BlockType.VISIT, 0);
        testBlock = memoryBlockJdbcService.create(block);

        // Create a test visit
        jdbcTemplate.update(
                "INSERT INTO visit (user_id, longitude, latitude, start_time, end_time, duration_seconds, processed, version) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                testUser.getId(), 24.9384, 60.1699,
                java.sql.Timestamp.from(java.time.Instant.now()),
                java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(3600)),
                3600L, false, 1L
        );

        testVisitId = jdbcTemplate.queryForObject(
                "SELECT id FROM visit WHERE user_id = ?",
                Long.class,
                testUser.getId()
        );
    }

    @Test
    void testCreateVisitBlock() {
        MemoryBlockVisit visitBlock = new MemoryBlockVisit(testBlock.getId(), testVisitId);

        MemoryBlockVisit created = memoryBlockVisitJdbcService.create(visitBlock);

        assertEquals(testBlock.getId(), created.getBlockId());
        assertEquals(testVisitId, created.getVisitId());
    }

    @Test
    void testFindByBlockId() {
        MemoryBlockVisit visitBlock = new MemoryBlockVisit(testBlock.getId(), testVisitId);
        memoryBlockVisitJdbcService.create(visitBlock);

        Optional<MemoryBlockVisit> found = memoryBlockVisitJdbcService.findByBlockId(testBlock.getId());

        assertTrue(found.isPresent());
        assertEquals(testBlock.getId(), found.get().getBlockId());
        assertEquals(testVisitId, found.get().getVisitId());
    }

    @Test
    void testDeleteVisitBlock() {
        MemoryBlockVisit visitBlock = new MemoryBlockVisit(testBlock.getId(), testVisitId);
        memoryBlockVisitJdbcService.create(visitBlock);

        memoryBlockVisitJdbcService.delete(testBlock.getId());

        Optional<MemoryBlockVisit> found = memoryBlockVisitJdbcService.findByBlockId(testBlock.getId());
        assertFalse(found.isPresent());
    }

    @Test
    void testFindByBlockIdNotFound() {
        Optional<MemoryBlockVisit> found = memoryBlockVisitJdbcService.findByBlockId(999L);
        assertFalse(found.isPresent());
    }
}
