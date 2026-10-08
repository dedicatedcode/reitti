package com.dedicatedcode.reitti.config;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.security.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;


import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class CsrfProtectionIntegrationTest {

    private static final String CSRF_COOKIE = "XSRF-TOKEN";

    /**
     * Forces this test class onto its own application context.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class DedicatedContext {
    }

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
    void getPage_shouldExposeReadableCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/login")).andExpect(status().isOk()).andReturn();

        Cookie cookie = result.getResponse().getCookie(CSRF_COOKIE);
        assertThat(cookie).as("CsrfCookieFilter must publish the token as a cookie").isNotNull();
        assertThat(cookie.isHttpOnly()).as("csrf.js must be able to read the cookie").isFalse();
        assertThat(cookie.getValue()).isNotBlank();
    }

    @Test
    void mutation_withCookieAndExpectedHeader_shouldNotBeForbidden() throws Exception {
        String token = fetchCsrfToken();

        mockMvc.perform(post("/memories")
                        .cookie(new MockCookie(CSRF_COOKIE, token))
                        .header("X-XSRF-TOKEN", token)
                        .with(user(testUser)))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("a valid CSRF token must not be rejected")
                        .isNotEqualTo(403));
    }

    @Test
    void mutation_withWrongHeaderName_shouldBeForbidden() throws Exception {
        String token = fetchCsrfToken();

        mockMvc.perform(post("/memories")
                        .cookie(new MockCookie(CSRF_COOKIE, token))
                        .header("X-CSRF-TOKEN", token)
                        .with(user(testUser)))
                .andExpect(status().isForbidden());
    }

    @Test
    void mutation_withSessionButNoToken_shouldBeForbidden() throws Exception {
        mockMvc.perform(post("/memories")
                        .with(user(testUser)))
                .andExpect(status().isForbidden());
    }

    @Test
    void mutation_withApiTokenHeader_shouldNotRequireCsrfToken() throws Exception {
        String apiToken = testingService.createApiToken(testUser, "csrf-probe", null).getToken();

        mockMvc.perform(post("/memories")
                        .header("X-API-Token", apiToken)
                        .with(user(testUser)))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("device clients cannot obtain a CSRF token and must stay exempt")
                        .isNotEqualTo(403));
    }

    @Test
    void browserApiMutation_withCookieAndExpectedHeader_shouldNotBeForbidden() throws Exception {
        String token = fetchCsrfToken();

        mockMvc.perform(post("/api/v2/workbench/commit")
                        .cookie(new MockCookie(CSRF_COOKIE, token))
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(user(testUser)))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("session-authenticated calls under /api must remain CSRF protected, not exempt")
                        .isNotEqualTo(403));
    }

    @Test
    void browserApiMutation_withoutToken_shouldBeForbidden() throws Exception {
        mockMvc.perform(post("/api/v2/workbench/commit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(user(testUser)))
                .andExpect(status().isForbidden());
    }

    private String fetchCsrfToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/login")).andReturn();
        Cookie cookie = result.getResponse().getCookie(CSRF_COOKIE);
        assertThat(cookie).isNotNull();
        return cookie.getValue();
    }
}