package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.ApiToken;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ApiTokenJdbcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class ApiTokenSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestingService testingService;
    @Autowired
    private ApiTokenJdbcService apiTokenJdbcService;

    private User owner;
    private User intruder;
    private Device ownerDevice;
    private ApiToken ownerToken;

    @BeforeEach
    void setUp() {
        owner = testingService.randomUser();
        intruder = testingService.randomUser();
        ownerDevice = testingService.findDefaultDevice(owner);
        ownerToken = testingService.createApiToken(owner, "owner-token", ownerDevice);
    }

    @Test
    void ownerCanRenameAndDeleteToken() throws Exception {
        mockMvc.perform(post("/settings/api-tokens/{id}/rename", ownerToken.getId())
                                .param("name", "renamed")
                                .with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("successMessage"));
        assertThat(apiTokenJdbcService.findById(ownerToken.getId()).orElseThrow().getName()).isEqualTo("renamed");

        mockMvc.perform(post("/settings/api-tokens/{id}/delete", ownerToken.getId())
                                .with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("successMessage"));
        assertThat(apiTokenJdbcService.findById(ownerToken.getId())).isEmpty();
    }

    @Test
    void otherUsersCanNotDeleteToken() throws Exception {
        mockMvc.perform(post("/settings/api-tokens/{id}/delete", ownerToken.getId())
                                .with(user(intruder)))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("errorMessage"));

        assertThat(apiTokenJdbcService.findById(ownerToken.getId())).isPresent();
    }

    @Test
    void otherUsersCanNotReadOrModifyToken() throws Exception {
        Device intruderDevice = testingService.findDefaultDevice(intruder);

        mockMvc.perform(get("/settings/api-tokens/{id}/edit", ownerToken.getId()).with(user(intruder)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/settings/api-tokens/{id}/link-form", ownerToken.getId()).with(user(intruder)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/settings/api-tokens/{id}/rename", ownerToken.getId())
                                .param("name", "hijacked")
                                .with(user(intruder)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/settings/api-tokens/link/{id}", ownerToken.getId())
                                .param("deviceId", intruderDevice.id().toString())
                                .with(user(intruder)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/settings/api-tokens/{id}/detach/{deviceId}", ownerToken.getId(), intruderDevice.id())
                                .with(user(intruder)))
                .andExpect(status().isNotFound());

        ApiToken unchanged = apiTokenJdbcService.findById(ownerToken.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("owner-token");
        assertThat(unchanged.getDevice().id()).isEqualTo(ownerDevice.id());
    }
}
