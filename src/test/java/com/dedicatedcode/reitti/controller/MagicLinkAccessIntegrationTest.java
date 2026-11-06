package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.memory.BlockType;
import com.dedicatedcode.reitti.model.memory.HeaderType;
import com.dedicatedcode.reitti.model.memory.Memory;
import com.dedicatedcode.reitti.model.memory.MemoryBlock;
import com.dedicatedcode.reitti.model.memory.MemoryTrip;
import com.dedicatedcode.reitti.model.memory.MemoryVisit;
import com.dedicatedcode.reitti.model.security.MagicLinkAccessLevel;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.MemoryBlockJdbcService;
import com.dedicatedcode.reitti.repository.MemoryJdbcService;
import com.dedicatedcode.reitti.repository.MemoryTripJdbcService;
import com.dedicatedcode.reitti.repository.MemoryVisitJdbcService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    @Autowired
    private MemoryJdbcService memoryJdbcService;
    @Autowired
    private MemoryBlockJdbcService memoryBlockJdbcService;
    @Autowired
    private MemoryVisitJdbcService memoryVisitJdbcService;
    @Autowired
    private MemoryTripJdbcService memoryTripJdbcService;

    private static final Instant MEMORY_START = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant MEMORY_END = Instant.parse("2024-01-07T00:00:00Z");

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

    @Test
    void memoryMagicLinkCanLoadItsOwnVisitsAndTrips() throws Exception {
        Memory memory = createMemory();
        MemoryBlock visitBlock = memoryBlockJdbcService.create(owner, new MemoryBlock(memory.getId(), BlockType.CLUSTER_VISIT, 0));

        MemoryVisit home = memoryVisitJdbcService.save(owner, createVisit("Home",
                MEMORY_START.plus(1, ChronoUnit.HOURS), MEMORY_START.plus(2, ChronoUnit.HOURS)), visitBlock.getId(), null);
        MemoryVisit start = memoryVisitJdbcService.save(owner, createVisit("Start",
                MEMORY_START.plus(3, ChronoUnit.HOURS), MEMORY_START.plus(4, ChronoUnit.HOURS)), visitBlock.getId(), null);
        MemoryVisit end = memoryVisitJdbcService.save(owner, createVisit("End",
                MEMORY_START.plus(5, ChronoUnit.HOURS), MEMORY_START.plus(6, ChronoUnit.HOURS)), visitBlock.getId(), null);

        MemoryBlock tripBlock = memoryBlockJdbcService.create(owner, new MemoryBlock(memory.getId(), BlockType.CLUSTER_TRIP, 1));
        memoryTripJdbcService.save(owner, new MemoryTrip(null, true, start, end, start.getEndTime(), end.getStartTime()), tripBlock.getId(), null);

        MockHttpSession session = accessMemoryWith(memory);

        mockMvc.perform(get("/api/v2/memories/visits/{memoryId}/{blockId}", memory.getId(), visitBlock.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value(home.getName()));
        mockMvc.perform(get("/api/v2/memories/trips/{memoryId}/{blockId}", memory.getId(), tripBlock.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].startVisit.name").value("Start"))
                .andExpect(jsonPath("$[0].endVisit.name").value("End"));
    }

    @Test
    void memoryMagicLinkCanNotLoadBlocksOfAnotherMemory() throws Exception {
        Memory ownMemory = createMemory();
        Memory otherMemory = createMemory();
        MemoryBlock otherBlock = memoryBlockJdbcService.create(owner, new MemoryBlock(otherMemory.getId(), BlockType.CLUSTER_VISIT, 0));

        MockHttpSession session = accessMemoryWith(ownMemory);

        mockMvc.perform(get("/api/v2/memories/visits/{memoryId}/{blockId}", ownMemory.getId(), otherBlock.getId()).session(session))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v2/memories/visits/{memoryId}/{blockId}", otherMemory.getId(), otherBlock.getId()).session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void mapMagicLinkCanNotLoadMemoryBlockData() throws Exception {
        Memory memory = createMemory();
        MemoryBlock block = memoryBlockJdbcService.create(owner, new MemoryBlock(memory.getId(), BlockType.CLUSTER_VISIT, 0));
        MockHttpSession session = accessWith(createToken(MagicLinkAccessLevel.FULL_ACCESS));

        mockMvc.perform(get("/api/v2/memories/visits/{memoryId}/{blockId}", memory.getId(), block.getId()).session(session))
                .andExpect(status().isForbidden());
    }

    private Memory createMemory() {
        return memoryJdbcService.create(owner, new Memory("Shared Memory", "", MEMORY_START, MEMORY_END, HeaderType.MAP, null));
    }

    private MemoryVisit createVisit(String name, Instant start, Instant end) {
        return new MemoryVisit(null, true, name, start, end, 53.5, 9.9, ZoneOffset.UTC);
    }

    private MockHttpSession accessMemoryWith(Memory memory) throws Exception {
        String rawToken = magicLinkTokenService.createMemoryShareToken(owner, memory.getId(), MagicLinkAccessLevel.MEMORY_VIEW_ONLY, 1);
        MvcResult result = mockMvc.perform(get("/memories/{id}", memory.getId()).param("mt", rawToken))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/memories/" + memory.getId()))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
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
