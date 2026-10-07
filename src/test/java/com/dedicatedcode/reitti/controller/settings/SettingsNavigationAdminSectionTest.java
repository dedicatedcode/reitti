package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class SettingsNavigationAdminSectionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestingService testingService;

    private User admin;

    @BeforeEach
    void setUp() {
        admin = testingService.admin();
    }

    @Test
    void settingsNav_AsAdmin_ShouldRenderServerSettingsSection() throws Exception {
        mockMvc.perform(get("/settings/places").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("settings-nav-separator")))
                .andExpect(content().string(containsString("Server Settings")))
                .andExpect(content().string(containsString("/settings/geocode-services")))
                .andExpect(content().string(containsString("/settings/logging")));
    }

    @Test
    void settingsNav_AsRegularUser_ShouldNotRenderServerSettingsSection() throws Exception {
        mockMvc.perform(get("/settings/places").with(user(testingService.randomUser())))
                .andExpect(status().isOk())
                // no dangling header for regular users
                .andExpect(content().string(not(containsString("settings-nav-separator"))))
                .andExpect(content().string(not(containsString("Server Settings"))))
                .andExpect(content().string(not(containsString("/settings/geocode-services"))))
                .andExpect(content().string(not(containsString("/settings/logging"))));
    }

    @Test
    void placesPage_ShouldRenderGeocodingExecutionActions() throws Exception {
        mockMvc.perform(get("/settings/places").with(user(testingService.randomUser())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/settings/places/run-geocoding")))
                .andExpect(content().string(containsString("/settings/places/clear-and-rerun")));
    }
}
