package com.dedicatedcode.reitti.config.security;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.ApiToken;
import com.dedicatedcode.reitti.model.security.DeviceTokenUser;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.ApiTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class TokenAuthenticationFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiTokenService apiTokenService;

    @Autowired
    private TestingService  testingService;

    private User user;
    private Device device;
    private ApiToken token;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        user = this.testingService.randomUser();
        device = this.testingService.findDefaultDevice(user);
        token = this.apiTokenService.getTokensForUser(user).stream().findFirst().orElseThrow();
    }

    @Test
    void whenValidXApiTokenProvided_thenRequestPassesAndContextIsSet() throws Exception {

        mockMvc.perform(get("/test-endpoint")
                                .header("X-API-Token", token.getToken()))
                .andExpect(status().isOk())
                .andExpect(content().string("Success " + user.getId() + " " + device.id()));
    }

    @Test
    void whenValidBearerTokenProvided_thenRequestPassesAndContextIsSet() throws Exception {

        mockMvc.perform(get("/test-endpoint")
                                .header("Authorization", "Bearer " + token.getToken()))
                .andExpect(status().isOk())
                .andExpect(content().string("Success " + user.getId() + " " + device.id()));
    }

    @Test
    void whenInvalidTokenProvided_thenReturns401Unauthorized() throws Exception {
        String invalidToken = "invalid-token";
        mockMvc.perform(get("/test-endpoint")
                                .header("X-API-Token", invalidToken))
                .andExpect(status().isUnauthorized());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void whenNoTokenProvided_thenReturns302Forbidden() throws Exception {
        mockMvc.perform(get("/test-endpoint"))
                .andExpect(status().is3xxRedirection());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    /**
     * Test Configuration to mirror your "all endpoints authenticated" setup
     * and provide a dummy controller.
     */
    @TestConfiguration
    static class SecurityTestConfig {

        @RestController
        static class DummyController {
            @GetMapping("/test-endpoint")
            public String testEndpoint(@AuthenticationPrincipal DeviceTokenUser user) {
                return "Success " + user.getId() + " " + user.getDevice().orElseThrow().id();
            }
        }
    }
}