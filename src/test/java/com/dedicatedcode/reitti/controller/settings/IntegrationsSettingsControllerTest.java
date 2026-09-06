package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.integration.ImmichIntegrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
}
