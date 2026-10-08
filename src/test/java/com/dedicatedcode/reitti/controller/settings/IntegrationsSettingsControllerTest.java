package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.integration.IntervalsIcuIntegration;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.IntervalsIcuIntegrationJdbcService;
import com.dedicatedcode.reitti.service.integration.ImmichIntegrationService;
import com.dedicatedcode.reitti.service.jobs.JobState;
import com.dedicatedcode.reitti.service.jobs.JobType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
class IntegrationsSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestingService testingService;

    @Autowired
    private ImmichIntegrationService immichIntegrationService;

    @Autowired
    private IntervalsIcuIntegrationJdbcService intervalsIcuIntegrationJdbcService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        testingService.clearData();
        SecurityContextHolder.clearContext();
    }

    private void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @Test
    void shouldRenderIntegrationsContentWithoutIntegration() throws Exception {
        User user = testingService.randomUser();
        authenticate(user);

        mockMvc.perform(get("/settings/integrations/integrations-content"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("immich-album-select")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"albumId\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"albumName\"")));
    }

    @Test
    void shouldRenderIntegrationsContentWithSavedAlbum() throws Exception {
        User user = testingService.randomUser();
        authenticate(user);

        immichIntegrationService.saveIntegration(user, "http://localhost:8089", "token", "album-1", "My Album", false, true);

        mockMvc.perform(get("/settings/integrations/integrations-content"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("album-1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("My Album")));
    }

    @Test
    void shouldSaveIntegrationWithAlbum() throws Exception {
        User user = testingService.randomUser();
        authenticate(user);

        mockMvc.perform(post("/settings/integrations/immich-integration")
                        .param("serverUrl", "http://localhost:8089")
                        .param("apiToken", "token")
                        .param("albumId", "album-1")
                        .param("albumName", "My Album")
                        .param("enabled", "true")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        var integration = immichIntegrationService.getIntegrationForUser(user).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(integration.getAlbumId()).isEqualTo("album-1");
        org.assertj.core.api.Assertions.assertThat(integration.getAlbumName()).isEqualTo("My Album");
    }

    @Test
    void shouldRenderAlbumSelectFragmentOnLoad() throws Exception {
        User user = testingService.randomUser();
        authenticate(user);

        mockMvc.perform(post("/settings/integrations/immich-integration/albums")
                        .param("serverUrl", "http://localhost:8089")
                        .param("apiToken", "token")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("immich-album-select")));
    }

    @Test
    void shouldRenderIntervalsIcuSectionInExternalDataStores() throws Exception {
        User user = testingService.randomUser();
        authenticate(user);

        mockMvc.perform(get("/settings/integrations/integrations-content"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-section=\"intervals-icu\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-section=\"owntracks-recorder\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"apiKey\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"reittiDeviceId\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/settings/integrations/intervals-icu-integration/sync")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/settings/integrations/intervals-icu-integration/load-historical")));
    }

    @Test
    void shouldSaveIntervalsIcuIntegration() throws Exception {
        User user = testingService.randomUser();
        authenticate(user);
        Device device = testingService.findDefaultDevice(user);

        mockMvc.perform(post("/settings/integrations/intervals-icu-integration")
                        .param("apiKey", "test-api-key")
                        .param("enabled", "true")
                        .param("reittiDeviceId", String.valueOf(device.id()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/settings/integrations/integrations-content?openSection=intervals-icu"));

        IntervalsIcuIntegration integration = intervalsIcuIntegrationJdbcService.findByUser(user).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(integration.getApiKey()).isEqualTo("test-api-key");
        org.assertj.core.api.Assertions.assertThat(integration.getReittiDeviceId()).isEqualTo(device.id());
        org.assertj.core.api.Assertions.assertThat(integration.isEnabled()).isTrue();
    }

    @Test
    void shouldRejectIntervalsIcuSaveWithoutApiKey() throws Exception {
        User user = testingService.randomUser();
        authenticate(user);
        Device device = testingService.findDefaultDevice(user);

        mockMvc.perform(post("/settings/integrations/intervals-icu-integration")
                        .param("apiKey", "")
                        .param("enabled", "true")
                        .param("reittiDeviceId", String.valueOf(device.id()))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", org.hamcrest.Matchers.containsString("API key")));

        org.assertj.core.api.Assertions.assertThat(intervalsIcuIntegrationJdbcService.findByUser(user)).isEmpty();
    }

    @Test
    void shouldRenderSavedIntervalsIcuConfiguration() throws Exception {
        User user = testingService.randomUser();
        authenticate(user);
        Device device = testingService.findDefaultDevice(user);
        intervalsIcuIntegrationJdbcService.save(user, new IntervalsIcuIntegration(
                "stored-key", device.id(), true)
                .withAthlete("i42", "Test Athlete")
                .withLastSuccessfulFetch(java.time.Instant.parse("2024-11-20T08:15:30Z")));

        mockMvc.perform(get("/settings/integrations/integrations-content"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("stored-key")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Test Athlete")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2024-11-20")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/settings/integrations/intervals-icu-integration/load-historical")));
    }

    @Test
    void shouldEnqueueTheIntervalsIcuHistoricalImport() throws Exception {
        User user = testingService.randomUser();
        authenticate(user);
        Device device = testingService.findDefaultDevice(user);
        // The athlete is already known so the background job only performs the activities request.
        intervalsIcuIntegrationJdbcService.save(user,
                new IntervalsIcuIntegration("stored-key", device.id(), true).withAthlete("i1", "Test Athlete"));

        // The endpoint queues a real Quartz job, so the shared RestTemplate has to stay stubbed until that
        // job has run. Otherwise the worker would call the live intervals.icu API from the test suite.
        ClientHttpRequestFactory original = restTemplate.getRequestFactory();
        MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);
        mockServer.expect(anything()).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        try {
            mockMvc.perform(post("/settings/integrations/intervals-icu-integration/load-historical")
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/settings/integrations/integrations-content?openSection=intervals-icu"))
                    .andExpect(flash().attributeExists("successMessage"));

            testingService.awaitExpected(db -> db.queryForObject(
                    "SELECT COUNT(*) FROM job_meta_data WHERE user_id = ? AND type = ? AND status = ?",
                    Integer.class, user.getId(), JobType.INTERVALS_ICU_IMPORT.name(), JobState.COMPLETED.name()) == 1, 60);
        } finally {
            restTemplate.setRequestFactory(original);
        }
    }

    @Test
    void shouldReportAFailureWhenTheIntervalsIcuHistoricalImportCannotBeEnqueued() throws Exception {
        User user = testingService.randomUser();
        authenticate(user);

        mockMvc.perform(post("/settings/integrations/intervals-icu-integration/load-historical")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage",
                        org.hamcrest.Matchers.containsString("No intervals.icu configuration")));
    }
}
