package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.memory.*;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
    private Trip testTrip;
    private ProcessedVisit startVisit;
    private ProcessedVisit endVisit;

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
                LocalDate.of(2024, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
                LocalDate.of(2024, 1, 7).atStartOfDay().toInstant(ZoneOffset.UTC),
                HeaderType.MAP,
                null
        );

        testMemory = memoryJdbcService.create(testUser, memory);

        MemoryBlock block = new MemoryBlock(testMemory.getId(), BlockType.TRIP, 0);
        testBlock = memoryBlockJdbcService.create(block);

        Instant now = Instant.now();
        SignificantPlace startPlace = this.significantPlaceJdbcService.create(testUser, SignificantPlace.create(60.1699, 24.9384));
        SignificantPlace endPlace = this.significantPlaceJdbcService.create(testUser, SignificantPlace.create(61.1700, 25.9385));

        startVisit = this.processedVisitJdbcService.create(testUser, new ProcessedVisit(startPlace, now, now.plus(20, ChronoUnit.MINUTES), 20*60L));
        endVisit = this.processedVisitJdbcService.create(testUser, new ProcessedVisit(endPlace, now.plus(3600, ChronoUnit.SECONDS), now.plus(80, ChronoUnit.MINUTES), 20*60L));

        Trip car = new Trip(startVisit.getEndTime(), endVisit.getStartTime(), 1000L, 100.0, 150.0, "WALKING", startVisit, endVisit);
        testTrip = tripJdbcService.create(testUser, car);
    }

    @Test
    void testCreateTripBlock() {
        MemoryBlockTrip tripBlock = new MemoryBlockTrip(
                testBlock.getId(),
                testTrip.getStartTime(),
                testTrip.getEndTime(),
                testTrip.getDurationSeconds(),
                testTrip.getEstimatedDistanceMeters(),
                testTrip.getTravelledDistanceMeters(),
                testTrip.getTransportModeInferred(),
                testTrip.getStartVisit().getPlace().getName(),
                testTrip.getStartVisit().getPlace().getLatitudeCentroid(),
                testTrip.getStartVisit().getPlace().getLongitudeCentroid(),
                testTrip.getEndVisit().getPlace().getName(),
                testTrip.getEndVisit().getPlace().getLatitudeCentroid(),
                testTrip.getEndVisit().getPlace().getLongitudeCentroid()
        );

        MemoryBlockTrip created = memoryBlockTripJdbcService.create(tripBlock);

        assertEquals(testBlock.getId(), created.getBlockId());
        assertEquals(testTrip.getStartTime(), created.getStartTime());
        assertEquals(testTrip.getEndTime(), created.getEndTime());
        assertEquals(testTrip.getDurationSeconds(), created.getDurationSeconds());
        assertEquals(testTrip.getEstimatedDistanceMeters(), created.getEstimatedDistanceMeters());
        assertEquals(testTrip.getTravelledDistanceMeters(), created.getTravelledDistanceMeters());
        assertEquals(testTrip.getTransportModeInferred(), created.getTransportModeInferred());
        assertEquals(testTrip.getStartVisit().getPlace().getName(), created.getStartPlaceName());
        assertEquals(testTrip.getStartVisit().getPlace().getLatitudeCentroid(), created.getStartLatitude());
        assertEquals(testTrip.getStartVisit().getPlace().getLongitudeCentroid(), created.getStartLongitude());
        assertEquals(testTrip.getEndVisit().getPlace().getName(), created.getEndPlaceName());
        assertEquals(testTrip.getEndVisit().getPlace().getLatitudeCentroid(), created.getEndLatitude());
        assertEquals(testTrip.getEndVisit().getPlace().getLongitudeCentroid(), created.getEndLongitude());
    }

    @Test
    void testFindByBlockId() {
        MemoryBlockTrip tripBlock = new MemoryBlockTrip(
                testBlock.getId(),
                testTrip.getStartTime(),
                testTrip.getEndTime(),
                testTrip.getDurationSeconds(),
                testTrip.getEstimatedDistanceMeters(),
                testTrip.getTravelledDistanceMeters(),
                testTrip.getTransportModeInferred(),
                testTrip.getStartVisit().getPlace().getName(),
                testTrip.getStartVisit().getPlace().getLatitudeCentroid(),
                testTrip.getStartVisit().getPlace().getLongitudeCentroid(),
                testTrip.getEndVisit().getPlace().getName(),
                testTrip.getEndVisit().getPlace().getLatitudeCentroid(),
                testTrip.getEndVisit().getPlace().getLongitudeCentroid()
        );
        memoryBlockTripJdbcService.create(tripBlock);

        Optional<MemoryBlockTrip> found = memoryBlockTripJdbcService.findByBlockId(testBlock.getId());

        assertTrue(found.isPresent());
        assertEquals(testBlock.getId(), found.get().getBlockId());
        assertEquals(testTrip.getStartTime(), found.get().getStartTime());
        assertEquals(testTrip.getEndTime(), found.get().getEndTime());
        assertEquals(testTrip.getDurationSeconds(), found.get().getDurationSeconds());
        assertEquals(testTrip.getEstimatedDistanceMeters(), found.get().getEstimatedDistanceMeters());
        assertEquals(testTrip.getTravelledDistanceMeters(), found.get().getTravelledDistanceMeters());
        assertEquals(testTrip.getTransportModeInferred(), found.get().getTransportModeInferred());
        assertEquals(testTrip.getStartVisit().getPlace().getName(), found.get().getStartPlaceName());
        assertEquals(testTrip.getStartVisit().getPlace().getLatitudeCentroid(), found.get().getStartLatitude());
        assertEquals(testTrip.getStartVisit().getPlace().getLongitudeCentroid(), found.get().getStartLongitude());
        assertEquals(testTrip.getEndVisit().getPlace().getName(), found.get().getEndPlaceName());
        assertEquals(testTrip.getEndVisit().getPlace().getLatitudeCentroid(), found.get().getEndLatitude());
        assertEquals(testTrip.getEndVisit().getPlace().getLongitudeCentroid(), found.get().getEndLongitude());
    }

    @Test
    void testDeleteTripBlock() {
        MemoryBlockTrip tripBlock = new MemoryBlockTrip(
                testBlock.getId(),
                testTrip.getStartTime(),
                testTrip.getEndTime(),
                testTrip.getDurationSeconds(),
                testTrip.getEstimatedDistanceMeters(),
                testTrip.getTravelledDistanceMeters(),
                testTrip.getTransportModeInferred(),
                testTrip.getStartVisit().getPlace().getName(),
                testTrip.getStartVisit().getPlace().getLatitudeCentroid(),
                testTrip.getStartVisit().getPlace().getLongitudeCentroid(),
                testTrip.getEndVisit().getPlace().getName(),
                testTrip.getEndVisit().getPlace().getLatitudeCentroid(),
                testTrip.getEndVisit().getPlace().getLongitudeCentroid()
        );
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
