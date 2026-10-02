package com.dedicatedcode.reitti.controller.api.ingestion.gpslogger;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.security.ApiToken;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.ApiTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GPSLogger "Log to custom URL" (#1261): the exact body/header combination shown on the integrations page must be
 * accepted, and a wrong token must be rejected.
 */
@IntegrationTest
class GPSLoggerIngestionApiControllerTest {

    // GPSLogger substitutes the placeholders as plain text, so every value arrives as a JSON string
    private static final String DOCUMENTED_BODY = """
            {"_type":"location","lat":"53.863149","lon":"10.700927","tst":"1700000000","acc":"8.5","alt":"12.0","vel":"0.0","batt":"77"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestingService testingService;

    @Autowired
    private ApiTokenService apiTokenService;

    private ApiToken token;

    @BeforeEach
    void setUp() {
        User user = testingService.randomUser();
        token = apiTokenService.getTokensForUser(user).stream().findFirst().orElseThrow();
    }

    @Test
    void documentedCustomUrlSetupIsAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/ingest/gpslogger")
                        .header("Authorization", "Bearer " + token.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DOCUMENTED_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void wrongTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/ingest/gpslogger")
                        .header("Authorization", "Bearer not-a-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DOCUMENTED_BODY))
                .andExpect(status().isUnauthorized());
    }
}
