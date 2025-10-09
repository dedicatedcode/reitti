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
import java.time.ZoneOffset;
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
    private ProcessedVisitJdbcService processedVisitJdbcService;

    @Autowired
    private SignificantPlaceJdbcService significantPlaceJdbcService;

    @Autowired
    private TestingService testingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User testUser;
    private Memory testMemory;
    private MemoryBlock testBlock;
    private ProcessedVisit testProcessedVisit;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM memory_block_visit");
        jdbcTemplate.update("DELETE FROM memory_block");
        jdbcTemplate.update("DELETE FROM memory");
        jdbcTemplate.update("DELETE FROM processed_visits");
        jdbcTemplate.update("DELETE FROM significant_places");

        testUser = testingService.randomUser();

        Memory memory = new Memory(
                "Test Memory",
                "Description",
                LocalDate.of(2024, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
                LocalDate.of(2024, 1, 7).atStartOfDay().toInstant(ZoneOffset.UTC),
                HeaderType.MAP,
                null
        );

        testMemory = memoryJdbcService.create(testUser, memory);

        MemoryBlock block = new MemoryBlock(testMemory.getId(), BlockType.VISIT, 0);
        testBlock = memoryBlockJdbcService.create(block);

        // Create a test significant place
        SignificantPlace place = significantPlaceJdbcService.create(testUser, SignificantPlace.create(60.1699, 24.9384));

        // Create a test processed visit
        ProcessedVisit processedVisit = new ProcessedVisit(
                place,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                3600L
        );

        testProcessedVisit = processedVisitJdbcService.create(testUser, processedVisit);
    }

    @Test
    void testCreateVisitBlock() {
        MemoryBlockVisit visitBlock = new MemoryBlockVisit(
                testBlock.getId(),
                testProcessedVisit.getId(),
                testProcessedVisit.getPlace().getName(),
                testProcessedVisit.getPlace().getAddress(),
                testProcessedVisit.getPlace().getLatitudeCentroid(),
                testProcessedVisit.getPlace().getLongitudeCentroid(),
                testProcessedVisit.getStartTime(),
                testProcessedVisit.getEndTime(),
                testProcessedVisit.getDurationSeconds()
        );

        MemoryBlockVisit created = memoryBlockVisitJdbcService.create(visitBlock);

        assertEquals(testBlock.getId(), created.getBlockId());
        assertEquals(testProcessedVisit.getId(), created.getOriginalProcessedVisitId());
        assertEquals(testProcessedVisit.getPlace().getName(), created.getPlaceName());
        assertEquals(testProcessedVisit.getPlace().getAddress(), created.getPlaceAddress());
        assertEquals(testProcessedVisit.getPlace().getLatitudeCentroid(), created.getLatitude());
        assertEquals(testProcessedVisit.getPlace().getLongitudeCentroid(), created.getLongitude());
        assertEquals(testProcessedVisit.getStartTime(), created.getStartTime());
        assertEquals(testProcessedVisit.getEndTime(), created.getEndTime());
        assertEquals(testProcessedVisit.getDurationSeconds(), created.getDurationSeconds());
    }

    @Test
    void testFindByBlockId() {
        MemoryBlockVisit visitBlock = new MemoryBlockVisit(
                testBlock.getId(),
                testProcessedVisit.getId(),
                testProcessedVisit.getPlace().getName(),
                testProcessedVisit.getPlace().getAddress(),
                testProcessedVisit.getPlace().getLatitudeCentroid(),
                testProcessedVisit.getPlace().getLongitudeCentroid(),
                testProcessedVisit.getStartTime(),
                testProcessedVisit.getEndTime(),
                testProcessedVisit.getDurationSeconds()
        );
        memoryBlockVisitJdbcService.create(visitBlock);

        Optional<MemoryBlockVisit> found = memoryBlockVisitJdbcService.findByBlockId(testBlock.getId());

        assertTrue(found.isPresent());
        assertEquals(testBlock.getId(), found.get().getBlockId());
        assertEquals(testProcessedVisit.getId(), found.get().getOriginalProcessedVisitId());
        assertEquals(testProcessedVisit.getPlace().getName(), found.get().getPlaceName());
        assertEquals(testProcessedVisit.getPlace().getAddress(), found.get().getPlaceAddress());
        assertEquals(testProcessedVisit.getPlace().getLatitudeCentroid(), found.get().getLatitude());
        assertEquals(testProcessedVisit.getPlace().getLongitudeCentroid(), found.get().getLongitude());
        assertEquals(testProcessedVisit.getStartTime(), found.get().getStartTime());
        assertEquals(testProcessedVisit.getEndTime(), found.get().getEndTime());
        assertEquals(testProcessedVisit.getDurationSeconds(), found.get().getDurationSeconds());
    }

    @Test
    void testDeleteVisitBlock() {
        MemoryBlockVisit visitBlock = new MemoryBlockVisit(
                testBlock.getId(),
                testProcessedVisit.getId(),
                testProcessedVisit.getPlace().getName(),
                testProcessedVisit.getPlace().getAddress(),
                testProcessedVisit.getPlace().getLatitudeCentroid(),
                testProcessedVisit.getPlace().getLongitudeCentroid(),
                testProcessedVisit.getStartTime(),
                testProcessedVisit.getEndTime(),
                testProcessedVisit.getDurationSeconds()
        );
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
