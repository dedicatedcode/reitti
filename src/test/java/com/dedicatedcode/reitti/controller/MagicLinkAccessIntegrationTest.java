package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.security.MagicLinkAccessLevel;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.MagicLinkTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class MagicLinkAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestingService testingService;
    @Autowired
    private MagicLinkTokenService magicLinkTokenService;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = testingService.randomUser();
    }

    @Test
    void fullAccessMagicLinkOpensTheMapView() throws Exception {
        MockHttpSession session = accessWith(createToken(MagicLinkAccessLevel.FULL_ACCESS));

        mockMvc.perform(get("/").session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/manifest.json").session(session)).andExpect(status().isOk());
        mockMvc.perform(get("/timeline/content/range")
                        .param("startDate", LocalDate.now(ZoneOffset.UTC).toString())
                        .param("endDate", LocalDate.now(ZoneOffset.UTC).toString())
                        .param("timezone", "UTC")
                        .session(session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v2/trips/{userId}", owner.getId())
                        .param("startDate", LocalDate.now(ZoneOffset.UTC).toString())
                        .param("endDate", LocalDate.now(ZoneOffset.UTC).toString())
                        .session(session))
                .andExpect(status().isOk());
        // 404 proves the request passed authorization; the owner simply has no avatar
        mockMvc.perform(get("/api/avatars/{userId}", owner.getId()).session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyLiveMagicLinkCanOnlyViewToday() throws Exception {
        MockHttpSession session = accessWith(createToken(MagicLinkAccessLevel.ONLY_LIVE));

        mockMvc.perform(get("/timeline/content/range")
                        .param("startDate", LocalDate.now(ZoneOffset.UTC).toString())
                        .param("endDate", LocalDate.now(ZoneOffset.UTC).toString())
                        .param("timezone", "UTC")
                        .session(session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/timeline/content/range")
                        .param("startDate", "2020-01-01")
                        .param("endDate", "2020-01-01")
                        .param("timezone", "UTC")
                        .session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void magicLinkSessionCanNotAccessSettingsOrMemories() throws Exception {
        MockHttpSession session = accessWith(createToken(MagicLinkAccessLevel.FULL_ACCESS));

        mockMvc.perform(get("/settings").session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/memories/all").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void invalidTokenRedirectsToErrorPage() throws Exception {
        mockMvc.perform(get("/access").param("mt", "does-not-exist"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/magic-link?error=invalid"));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String rawToken = magicLinkTokenService.createMapShareToken(owner, "expired-link", MagicLinkAccessLevel.FULL_ACCESS,
                Instant.now().minus(1, ChronoUnit.DAYS));

        mockMvc.perform(get("/access").param("mt", rawToken))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/magic-link?error=invalid"));
    }

    private String createToken(MagicLinkAccessLevel level) {
        return magicLinkTokenService.createMapShareToken(owner, "test-link", level,
                Instant.now().plus(1, ChronoUnit.DAYS));
    }

    private MockHttpSession accessWith(String rawToken) throws Exception {
        MvcResult result = mockMvc.perform(get("/access").param("mt", rawToken))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
