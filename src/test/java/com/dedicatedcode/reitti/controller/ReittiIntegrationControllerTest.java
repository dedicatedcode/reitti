package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.integration.ReittiIntegration;
import com.dedicatedcode.reitti.model.security.RemoteUser;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.ReittiIntegrationJdbcService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
class ReittiIntegrationControllerTest {

    private static final byte[] PNG = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestingService testingService;

    @Autowired
    private ReittiIntegrationJdbcService reittiIntegrationJdbcService;

    private User owner;
    private User otherUser;
    private ReittiIntegration integration;

    @BeforeEach
    void setUp() {
        owner = testingService.randomUser();
        otherUser = testingService.randomUser();
        integration = reittiIntegrationJdbcService.create(owner, new ReittiIntegration(null, "http://192.168.1.20:8080", "remote-token",
                true, ReittiIntegration.Status.ACTIVE, LocalDateTime.now(), null, null, null, null, "#ff0000"));
    }

    @AfterEach
    void tearDown() {
        testingService.clearData();
    }

    @Test
    void ownerGetsAvatarWithDetectedContentType() throws Exception {
        // the remote server claimed a different type, the stored bytes decide
        reittiIntegrationJdbcService.store(integration, new RemoteUser(1L, "Remote", "remote", 1L), PNG, "image/jpeg");

        mockMvc.perform(get("/reitti-integration/avatar/" + integration.getId()).with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void otherUsersCannotReadTheAvatar() throws Exception {
        reittiIntegrationJdbcService.store(integration, new RemoteUser(1L, "Remote", "remote", 1L), PNG, "image/png");

        mockMvc.perform(get("/reitti-integration/avatar/" + integration.getId()).with(user(otherUser)))
                .andExpect(status().isNotFound());
    }

    @Test
    void storedNonImageContentIsNotServed() throws Exception {
        byte[] html = "<html><script>alert(document.cookie)</script></html>".getBytes(StandardCharsets.UTF_8);
        reittiIntegrationJdbcService.store(integration, new RemoteUser(1L, "Remote", "remote", 1L), html, "text/html");

        mockMvc.perform(get("/reitti-integration/avatar/" + integration.getId()).with(user(owner)))
                .andExpect(status().isNotFound());
    }
}
