package com.dedicatedcode.reitti.controller.api.ingestion.owntracks;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.dto.OwntracksFriendResponse;
import com.dedicatedcode.reitti.dto.OwntracksLocationRequest;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.UserSharingJdbcService;
import com.dedicatedcode.reitti.service.AvatarService;
import com.dedicatedcode.reitti.service.LocationBatchingService;
import com.dedicatedcode.reitti.service.integration.ReittiIntegrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
@AutoConfigureWebMvc
class OwntracksIngestionApiControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestingService testingService;

    @Autowired
    private UserSharingJdbcService userSharingJdbcService;

    @Autowired
    private RawLocationPointJdbcService rawLocationPointJdbcService;

    @Autowired
    private AvatarService avatarService;

    @MockBean
    private LocationBatchingService locationBatchingService;

    @MockBean
    private ReittiIntegrationService reittiIntegrationService;

    private User testUser;
    private User sharedUser;

    @BeforeEach
    void setUp() {
        testUser = testingService.randomUser();
        sharedUser = testingService.randomUser();

        // Create sharing relationship - sharedUser shares with testUser
        userSharingJdbcService.createSharing(sharedUser.getId(), testUser.getId(), "#FF0000");

        // Add some location data for shared user
        rawLocationPointJdbcService.save(new RawLocationPoint(
                null,
                sharedUser.getId(),
                60.1699,
                24.9384,
                10.0,
                50.0,
                10.0,  // altitude
                10.0,  // vertical accuracy
                10.0,  // speed
                10.0,  // course
                10.0,  // battery
                10.0,  // hdop
                10.0,  // vdop
                10.0,  // pdop
                10.0,  // hacc
                10.0,  // vacc
                10.0,  // sacc
                10.0,  // cacc
                10.0,  // hdop
                10.0,  // vdop
                10.0,  // pdop
                10.0,  // hacc
                10.0,  // vacc
                10.0,  // sacc
                10.0,  // cacc
                10.0,  // hdop
                10.0,  // vdop
                10.0,  // pdop
                10.0,  // hacc
                10.0,  // vacc
                10.0,  // sacc
                10.0,  // cacc
                Instant.now()
        ));
    }

    @Test
    void testOwntracksIngestWithLocation() throws Exception {
        String owntracksPayload = """
                {
                    "_type": "location",
                    "lat": 53.863149,
                    "lon": 10.700927,
                    "tst": 1699545600,
                    "acc": 10.5,
                    "alt": 42.5
                }
                """;

        mockMvc.perform(post("/api/v1/ingest/owntracks")
                        .with(user(testUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(owntracksPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Successfully queued Owntracks location point for processing"));
    }

    @Test
    void testOwntracksIngestIgnoresNonLocationMessages() throws Exception {
        String owntracksPayload = """
                {
                    "_type": "waypoint",
                    "lat": 53.863149,
                    "lon": 10.700927,
                    "tst": 1699545600
                }
                """;

        mockMvc.perform(post("/api/v1/ingest/owntracks")
                        .with(user(testUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(owntracksPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Non-location update ignored"));
    }

    @Test
    void testOwntracksIngestReturnsFriendsData() throws Exception {
        String owntracksPayload = """
                {
                    "_type": "location",
                    "lat": 53.863149,
                    "lon": 10.700927,
                    "tst": 1699545600,
                    "acc": 10.5
                }
                """;

        // Mock the location batching service to avoid actual processing
        doNothing().when(locationBatchingService).addLocationPoint(any(User.class), any(OwntracksLocationRequest.class));

        MvcResult result = mockMvc.perform(post("/api/v1/ingest/owntracks")
                        .with(user(testUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(owntracksPayload))
                .andExpect(status().isOk())
                .andReturn();

        // Parse the response to check friends data
        String responseContent = result.getResponse().getContentAsString();
        assertTrue(responseContent.contains("friends"), "Response should contain friends data");

        // Verify location batching was called
        verify(locationBatchingService, times(1)).addLocationPoint(any(User.class), any(OwntracksLocationRequest.class));
    }

    @Test
    void testOwntracksIngestWithSharedUserLocation() throws Exception {
        String owntracksPayload = """
                {
                    "_type": "location",
                    "lat": 53.863149,
                    "lon": 10.700927,
                    "tst": 1699545600,
                    "acc": 10.5
                }
                """;

        // Mock the location batching service
        doNothing().when(locationBatchingService).addLocationPoint(any(User.class), any(OwntracksLocationRequest.class));

        MvcResult result = mockMvc.perform(post("/api/v1/ingest/owntracks")
                        .with(user(testUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(owntracksPayload))
                .andExpect(status().isOk())
                .andReturn();

        // Parse the response to check if shared user's location is included
        String responseContent = result.getResponse().getContentAsString();
        assertTrue(responseContent.contains("friends"), "Response should contain friends data");
        assertTrue(responseContent.contains("Shared User"), "Response should contain shared user's name");
    }

    @Test
    void testOwntracksIngestWithInvalidPayload() throws Exception {
        String invalidPayload = """
                {
                    "invalid": "payload"
                }
                """;

        mockMvc.perform(post("/api/v1/ingest/owntracks")
                        .with(user(testUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest());
    }
}
