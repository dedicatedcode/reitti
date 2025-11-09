package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureWebMvc
class IngestApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestingService testingService;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = testingService.randomUser();
    }

    @Test
    void testOwntracksIngestWithoutElevation() throws Exception {
        String owntracksPayload = """
                {
                    "_type": "location",
                    "lat": 53.863149,
                    "lon": 10.700927,
                    "tst": 1699545600,
                    "acc": 10.5
                }
                """;

        mockMvc.perform(post("/api/v1/ingest/owntracks")
                        .with(user(testUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(owntracksPayload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Successfully queued Owntracks location point for processing"));
    }

    @Test
    void testOwntracksIngestWithElevation() throws Exception {
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
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Successfully queued Owntracks location point for processing"));
    }

    @Test
    void testOverlandIngestWithoutElevation() throws Exception {

        String overlandPayload = """
                {
                    "locations": [
                        {
                            "type": "Feature",
                            "geometry": {
                                "type": "Point",
                                "coordinates": [10.700927, 53.863149]
                            },
                            "properties": {
                                "timestamp": "2023-11-09T12:00:00Z",
                                "horizontal_accuracy": 10.5
                            }
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/v1/ingest/overland")
                        .with(user(testUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(overlandPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("ok"));
    }

    @Test
    void testOverlandIngestWithElevation() throws Exception {
        String overlandPayload = """
                {
                    "locations": [
                        {
                            "type": "Feature",
                            "geometry": {
                                "type": "Point",
                                "coordinates": [10.700927, 53.863149, 42.5]
                            },
                            "properties": {
                                "timestamp": "2023-11-09T12:00:00Z",
                                "horizontal_accuracy": 10.5,
                                "altitude": 42.5
                            }
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/v1/ingest/overland")
                        .with(user(testUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(overlandPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("ok"));
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
    void testOverlandIngestWithEmptyLocations() throws Exception {

        String overlandPayload = """
                {
                    "locations": []
                }
                """;

        mockMvc.perform(post("/api/v1/ingest/overland")
                        .with(user(testUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(overlandPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("ok"));
    }
}
