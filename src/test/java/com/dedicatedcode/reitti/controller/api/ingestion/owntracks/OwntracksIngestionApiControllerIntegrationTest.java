package com.dedicatedcode.reitti.controller.api.ingestion.owntracks;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.dto.LocationPoint;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSharing;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.UserSharingJdbcService;
import com.dedicatedcode.reitti.service.AvatarService;
import com.dedicatedcode.reitti.service.LocationBatchingService;
import com.dedicatedcode.reitti.service.integration.ReittiIntegrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @MockitoBean
    private LocationBatchingService locationBatchingService;

    @MockitoBean
    private ReittiIntegrationService reittiIntegrationService;

    private User testUser;
    private User sharedUser;

    @BeforeEach
    void setUp() {
        testUser = testingService.randomUser();
        sharedUser = testingService.randomUser();

        // Create sharing relationship - sharedUser shares with testUser
        userSharingJdbcService.create(sharedUser, Set.of(new UserSharing(null, null, testUser.getId(), null, "#FF0000", null)));

        rawLocationPointJdbcService.create(sharedUser, new RawLocationPoint(Instant.now(), new GeoPoint(60.1699, 24.9384), 10.0));
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
                .andExpect(jsonPath("$").isArray());
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
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
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
        doNothing().when(locationBatchingService).addLocationPoint(any(User.class), any(LocationPoint.class));

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
        verify(locationBatchingService, times(1)).addLocationPoint(any(User.class), any(LocationPoint.class));
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
        doNothing().when(locationBatchingService).addLocationPoint(any(User.class), any(LocationPoint.class));

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
