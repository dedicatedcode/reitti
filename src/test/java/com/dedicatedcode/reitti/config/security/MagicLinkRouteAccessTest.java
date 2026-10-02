package com.dedicatedcode.reitti.config.security;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.memory.HeaderType;
import com.dedicatedcode.reitti.model.memory.Memory;
import com.dedicatedcode.reitti.model.security.MagicLinkAccessLevel;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.MemoryJdbcService;
import com.dedicatedcode.reitti.service.MagicLinkTokenService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Magic links are bearer URLs handed to people without an account. Each access level must only reach the
 * routes its view needs; everything else is denied by default.
 */
@IntegrationTest
class MagicLinkRouteAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestingService testingService;

    @Autowired
    private MagicLinkTokenService magicLinkTokenService;

    @Autowired
    private MemoryJdbcService memoryJdbcService;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = testingService.randomUser();
    }

    @Test
    void fullAccessLinkIsReadOnly() throws Exception {
        MockHttpSession session = open(magicLinkTokenService.createMapShareToken(owner, "full", MagicLinkAccessLevel.FULL_ACCESS, null), "/");
        long id = owner.getId();

        mockMvc.perform(get("/api/v1/visits/" + id).session(session)
                        .param("startDate", "2026-01-01T00:00").param("endDate", "2026-01-01T23:59:59").param("timezone", "UTC"))
                .andExpect(status().isOk());

        // nothing that changes data or reveals more than the timeline
        mockMvc.perform(post("/api/v2/workbench/commit").session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/settings/api-tokens").session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/statistics").session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/memories").session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/reitti-integration/timeline").session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v2/locations/geojson").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void lastLocationLinkOnlyReachesTheLatestLocation() throws Exception {
        MockHttpSession session = open(magicLinkTokenService.createMapShareToken(owner, "last", MagicLinkAccessLevel.ONLY_LAST_LOCATION, null), "/");
        long id = owner.getId();

        mockMvc.perform(get("/api/v2/locations/stream/" + id).session(session)
                        .param("start", "2026-01-01T00:00").param("end", "2026-01-01T23:59:59").param("timezone", "UTC"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/visits/" + id).session(session)
                        .param("startDate", "2026-01-01T00:00").param("endDate", "2026-01-01T23:59:59").param("timezone", "UTC"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/raw-location-points/" + id).session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v2/trips/" + id).session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/coverage").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void liveLinkCannotReadVisitsOrHistory() throws Exception {
        MockHttpSession session = open(magicLinkTokenService.createMapShareToken(owner, "live", MagicLinkAccessLevel.ONLY_LIVE, null), "/");
        long id = owner.getId();

        mockMvc.perform(get("/api/v1/visits/" + id).session(session)
                        .param("startDate", "2026-01-01T00:00").param("endDate", "2026-01-01T23:59:59").param("timezone", "UTC"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/photos/immich/range").session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/statistics/overall").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void liveLinkGetsTodaysTrackButNoHistory() throws Exception {
        MockHttpSession session = open(magicLinkTokenService.createMapShareToken(owner, "live", MagicLinkAccessLevel.ONLY_LIVE, null), "/");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        mockMvc.perform(get("/api/v2/locations/stream/" + owner.getId()).session(session)
                        .param("start", today + "T00:00").param("end", today + "T23:59:59").param("timezone", "UTC"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v2/locations/stream/" + owner.getId()).session(session)
                        .param("start", "2020-01-01T00:00").param("end", "2020-01-01T23:59:59").param("timezone", "UTC"))
                .andExpect(status().isForbidden());
    }

    @Test
    void memoryLinkGetsTheTrackOfItsMemoryOnly() throws Exception {
        Memory memory = memoryJdbcService.create(owner, new Memory("m", "d",
                LocalDate.of(2024, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
                LocalDate.of(2024, 1, 2).atStartOfDay().toInstant(ZoneOffset.UTC), HeaderType.MAP, null));
        MockHttpSession session = open(magicLinkTokenService.createMemoryShareToken(owner, memory.getId(), MagicLinkAccessLevel.MEMORY_VIEW_ONLY, 1),
                "/memories/" + memory.getId());

        // memory pages load their tracks from the stream API (#813)
        mockMvc.perform(get("/api/v2/locations/stream/" + owner.getId()).session(session)
                        .param("start", "2024-01-01T00:00").param("end", "2024-01-01T23:59:59").param("timezone", "UTC"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v2/locations/stream/" + owner.getId()).session(session)
                        .param("start", "2025-06-01T00:00").param("end", "2025-06-01T23:59:59").param("timezone", "UTC"))
                .andExpect(status().isForbidden());
    }

    @Test
    void memoryLinkOpenedViaAccessIsBoundToItsMemory() throws Exception {
        Memory memory = memoryJdbcService.create(owner, new Memory("m", "d",
                LocalDate.of(2024, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
                LocalDate.of(2024, 1, 2).atStartOfDay().toInstant(ZoneOffset.UTC), HeaderType.MAP, null));
        Memory other = memoryJdbcService.create(owner, new Memory("other", "d",
                LocalDate.of(2024, 2, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
                LocalDate.of(2024, 2, 2).atStartOfDay().toInstant(ZoneOffset.UTC), HeaderType.MAP, null));
        String token = magicLinkTokenService.createMemoryShareToken(owner, memory.getId(), MagicLinkAccessLevel.MEMORY_VIEW_ONLY, 1);

        // opened via the generic /access entry point it still only grants its own memory
        MockHttpSession session = open(token, "/memories/" + memory.getId());

        mockMvc.perform(get("/memories/all").session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/memories").session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/memories/years-navigation").session(session)).andExpect(status().isForbidden());
        // view-only links cannot change anything
        mockMvc.perform(post("/memories/" + memory.getId() + "/share").session(session)
                        .param("accessLevel", "FULL_ACCESS").param("validDays", "0"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/memories/" + other.getId()).session(session))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(403, 404));
    }

    @Test
    void ownerOpeningOwnLinkKeepsFullSession() throws Exception {
        String token = magicLinkTokenService.createMapShareToken(owner, "own", MagicLinkAccessLevel.ONLY_LAST_LOCATION, null);
        MvcResult result = mockMvc.perform(get("/access").param("mt", token).with(user(owner)))
                .andExpect(redirectedUrl("/"))
                .andReturn();
        // the owner is not downgraded to a restricted magic-link session (#813)
        HttpSession session = result.getRequest().getSession(false);
        Object context = session == null ? null : session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context == null || !(((SecurityContext) context).getAuthentication() instanceof MagicLinkAuthenticationToken)).isTrue();
    }

    private MockHttpSession open(String token, String expectedTarget) throws Exception {
        MvcResult result = mockMvc.perform(get("/access").param("mt", token))
                .andExpect(redirectedUrl(expectedTarget))
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }
}
