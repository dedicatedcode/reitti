package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.memory.BlockType;
import com.dedicatedcode.reitti.model.memory.Memory;
import com.dedicatedcode.reitti.model.memory.MemoryBlock;
import com.dedicatedcode.reitti.model.memory.MemoryTrip;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class MemoryBlockGenerationWithNonGeocodedPlaceControllerIT {

    @Autowired
    private TestingService testingService;
    @Autowired
    private SignificantPlaceJdbcService significantPlaceJdbcService;
    @Autowired
    private ProcessedVisitJdbcService processedVisitJdbcService;
    @Autowired
    private TripJdbcService tripJdbcService;
    @Autowired
    private MemoryJdbcService memoryJdbcService;
    @Autowired
    private MemoryBlockJdbcService memoryBlockJdbcService;

    private MockMvc mockMvc;
    private User user;
    private SignificantPlace nonGeocodedPlace;
    @Autowired
    private MemoryTripJdbcService memoryTripJdbcService;

    @BeforeEach
    void setUp(WebApplicationContext webApplicationContext) {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        // Create a unique user for this test run
        user = testingService.randomUser();
        // Create a non-geocoded place (name will be null)
        nonGeocodedPlace = significantPlaceJdbcService.create(user, SignificantPlace.create(45.0, 5.0));
        Assertions.assertNull(nonGeocodedPlace.getName(), "Place should not be geocoded, name must be null");
    }

    @Test
    void fullChain_withNonGeocodedPlace_shouldGenerateMemoryBlocks() throws Exception {
        // 1. Create a ProcessedVisit
        ProcessedVisit visit = new ProcessedVisit(
                nonGeocodedPlace,
                Instant.parse("2023-01-01T10:00:00Z"),
                Instant.parse("2023-01-01T12:00:00Z"),
                7200L
        );

        ProcessedVisit otherVisit = new ProcessedVisit(
                nonGeocodedPlace,
                Instant.parse("2023-01-01T14:00:00Z"),
                Instant.parse("2023-01-01T16:00:00Z"),
                7200L
        );
        ProcessedVisit savedVisit = this.processedVisitJdbcService.create(user, visit);
        ProcessedVisit savedOtherVisit = this.processedVisitJdbcService.create(user, otherVisit);

        Assertions.assertNotNull(savedVisit.getId());
        Assertions.assertNotNull(savedOtherVisit.getId());

        // 2. Create a Trip
        Trip trip = new Trip(savedVisit.getEndTime(),
                             savedOtherVisit.getStartTime(),
                             Duration.ofHours(2).getSeconds(),
                             10000.0,
                             10000.0,
                             TransportMode.DRIVING,
                             savedVisit,
                             savedOtherVisit);

        Trip savedTrip = this.tripJdbcService.create(user, trip);

        // 3. Create a Memory
        String memoryTitle = "Test Memory " + System.currentTimeMillis();
        mockMvc.perform(post("/memories")
                                .param("title", memoryTitle)
                                .param("description", "Description")
                                .param("startDate", "2023-01-01")
                                .param("startTime", "00:00")
                                .param("openEnded", "true")
                                .with(csrf())
                                .with(user(user)))
                .andExpect(status().isOk());


        List<Memory> allByUser = this.memoryJdbcService.findAllByUser(user);
        assertEquals(1, allByUser.size());
        Memory savedMemory = allByUser.getFirst();
        assertEquals(memoryTitle, savedMemory.getTitle());
        mockMvc.perform(post("/memories/{memoryId}/blocks/cluster", savedMemory.getId())
                                .param("selectedParts", new String[]{savedTrip.getId() + ""})
                                .param("type", "CLUSTER_TRIP")
                                .param("title", "Test Cluster Trip")
                                .with(csrf())
                                .with(user(user)))
                .andExpect(status().isOk());

        List<MemoryBlock> byMemoryId = this.memoryBlockJdbcService.findByMemoryId(savedMemory.getId())
                .stream().filter(b -> b.getBlockType() == BlockType.CLUSTER_TRIP).toList();
        assertEquals(1, byMemoryId.size());
        MemoryBlock clusterBlock = byMemoryId.getFirst();
        assertEquals(BlockType.CLUSTER_TRIP, clusterBlock.getBlockType());
        List<MemoryTrip> byMemoryBlockId = memoryTripJdbcService.findByMemoryBlockId(clusterBlock.getId());
        assertEquals(1, byMemoryBlockId.size());
        MemoryTrip tripInCluster = byMemoryBlockId.getFirst();
        assertEquals("45,0000, 5,0000", tripInCluster.getStartVisit().getName());
        assertEquals("45,0000, 5,0000", tripInCluster.getEndVisit().getName());
    }


    @Test
    public void shouldLoadTripAndVisitFragmentsCorrectlyWhenPlaceIsNotGeocoded() throws Exception {
        // 1. Create a ProcessedVisit
        ProcessedVisit visit = new ProcessedVisit(
                nonGeocodedPlace,
                Instant.parse("2023-01-01T10:00:00Z"),
                Instant.parse("2023-01-01T12:00:00Z"),
                7200L
        );

        ProcessedVisit otherVisit = new ProcessedVisit(
                nonGeocodedPlace,
                Instant.parse("2023-01-01T14:00:00Z"),
                Instant.parse("2023-01-01T16:00:00Z"),
                7200L
        );
        ProcessedVisit savedVisit = this.processedVisitJdbcService.create(user, visit);
        ProcessedVisit savedOtherVisit = this.processedVisitJdbcService.create(user, otherVisit);

        Assertions.assertNotNull(savedVisit.getId());
        Assertions.assertNotNull(savedOtherVisit.getId());

        // 2. Create a Trip
        Trip trip = new Trip(savedVisit.getEndTime(),
                             savedOtherVisit.getStartTime(),
                             Duration.ofHours(2).getSeconds(),
                             10000.0,
                             10000.0,
                             TransportMode.DRIVING,
                             savedVisit,
                             savedOtherVisit);

        Trip savedTrip = this.tripJdbcService.create(user, trip);

        // 3. Create a Memory
        String memoryTitle = "Test Memory " + System.currentTimeMillis();
        mockMvc.perform(post("/memories")
                                .param("title", memoryTitle)
                                .param("description", "Description")
                                .param("startDate", "2023-01-01")
                                .param("startTime", "00:00")
                                .param("openEnded", "true")
                                .with(csrf())
                                .with(user(user)))
                .andExpect(status().isOk());


        List<Memory> allByUser = this.memoryJdbcService.findAllByUser(user);
        assertEquals(1, allByUser.size());
        Memory savedMemory = allByUser.getFirst();
        assertEquals(memoryTitle, savedMemory.getTitle());
        mockMvc.perform(post("/memories/{memoryId}/blocks/cluster", savedMemory.getId())
                                .param("selectedParts", new String[]{savedTrip.getId() + ""})
                                .param("type", "CLUSTER_TRIP")
                                .param("title", "Test Cluster Trip")
                                .with(csrf())
                                .with(user(user)))
                .andExpect(status().isOk());

        List<MemoryBlock> byMemoryId = this.memoryBlockJdbcService.findByMemoryId(savedMemory.getId())
                .stream().filter(b -> b.getBlockType() == BlockType.CLUSTER_TRIP).toList();
        assertEquals(1, byMemoryId.size());

        mockMvc.perform(get("/memories/{memoryId}/blocks/new", savedMemory.getId())
                                .with(user(user))
                                .param("type", "TRIP_CLUSTER"))
                .andExpectAll(
                        status().isOk(),
                        model().attributeExists("availableTrips"));

        mockMvc.perform(get("/memories/{memoryId}/blocks/new", savedMemory.getId())
                                .with(user(user))
                                .param("type", "VISIT_CLUSTER"))
                .andExpectAll(
                        status().isOk(),
                        model().attributeExists("availableVisits"));
    }
}
