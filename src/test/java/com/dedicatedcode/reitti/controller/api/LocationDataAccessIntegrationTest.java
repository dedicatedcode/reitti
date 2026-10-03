package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.memory.HeaderType;
import com.dedicatedcode.reitti.model.memory.Memory;
import com.dedicatedcode.reitti.model.security.MagicLinkAccessLevel;
import com.dedicatedcode.reitti.model.security.MagicLinkResourceType;
import com.dedicatedcode.reitti.model.security.TokenUser;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSharing;
import com.dedicatedcode.reitti.repository.MemoryJdbcService;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import com.dedicatedcode.reitti.repository.UserSharingJdbcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class LocationDataAccessIntegrationTest {

    private static final Instant MEMORY_START = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant MEMORY_END = Instant.parse("2024-01-07T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestingService testingService;
    @Autowired
    private RawLocationPointJdbcService rawLocationPointJdbcService;
    @Autowired
    private UserSharingJdbcService userSharingJdbcService;
    @Autowired
    private MemoryJdbcService memoryJdbcService;

    private User owner;
    private User other;
    private Instant startOfToday;

    @BeforeEach
    void setUp() {
        owner = testingService.randomUser();
        other = testingService.randomUser();
        startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();

        addPoint(owner, MEMORY_START.plus(2, ChronoUnit.DAYS));
        addPoint(owner, MEMORY_END.plus(3, ChronoUnit.DAYS));
        addPoint(owner, startOfToday.minus(1, ChronoUnit.HOURS));
        addPoint(owner, startOfToday.plusSeconds(1));
    }

    @Test
    void rawPointsOfOtherUserAreForbiddenWithoutSharing() throws Exception {
        mockMvc.perform(get("/api/v1/raw-location-points/{userId}", owner.getId())
                                .param("startDate", "2024-01-01").param("endDate", "2024-01-31")
                                .with(user(other)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v2/locations/metadata/{userId}", owner.getId())
                                .param("start", "2024-01-01").param("end", "2024-01-31")
                                .with(user(other)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v2/locations/stream/{userId}", owner.getId())
                                .param("start", "2024-01-01").param("end", "2024-01-31")
                                .with(user(other)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v2/trips/{userId}", owner.getId())
                                .param("startDate", "2024-01-01").param("endDate", "2024-01-31")
                                .with(user(other)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/visits/{userId}", owner.getId())
                                .param("startDate", "2024-01-01").param("endDate", "2024-01-31")
                                .with(user(other)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v2/coverage/cells/{userId}", owner.getId())
                                .param("start", "2024-01-01").param("end", "2024-01-31")
                                .with(user(other)))
                .andExpect(status().isForbidden());
    }

    @Test
    void rawPointsOfOtherUserAreReadableWhenShared() throws Exception {
        shareWith(owner, other);

        mockMvc.perform(get("/api/v1/raw-location-points/{userId}", owner.getId())
                                .param("startDate", "2024-01-01").param("endDate", "2024-01-31")
                                .with(user(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latest").exists());
        mockMvc.perform(get("/api/v2/locations/metadata/{userId}", owner.getId())
                                .param("start", "2024-01-01").param("end", "2024-01-31")
                                .with(user(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPoints").value(2));
    }

    @Test
    void magicLinkCanNotReadUsersSharingWithTheOwner() throws Exception {
        shareWith(other, owner);
        RequestPostProcessor link = magicLink(owner, MagicLinkAccessLevel.FULL_ACCESS);

        mockMvc.perform(get("/api/v1/raw-location-points/{userId}", other.getId())
                                .param("startDate", "2024-01-01").param("endDate", "2024-01-31")
                                .with(link))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v2/locations/metadata/{userId}", other.getId())
                                .param("start", "2024-01-01").param("end", "2024-01-31")
                                .with(link))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v2/coverage/cells/{userId}", other.getId())
                                .param("start", "2024-01-01").param("end", "2024-01-31")
                                .with(link))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v2/coverage/cells/{userId}", owner.getId())
                                .param("start", "2024-01-01").param("end", "2024-01-31")
                                .with(link))
                .andExpect(status().isOk());
    }

    @Test
    void fullAccessLinkReadsAnyRangeOfTheOwner() throws Exception {
        mockMvc.perform(get("/api/v2/locations/metadata/{userId}", owner.getId())
                                .param("start", "2024-01-01").param("end", "2024-01-31")
                                .with(magicLink(owner, MagicLinkAccessLevel.FULL_ACCESS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPoints").value(2));
    }

    @Test
    void liveLinkOnlyReadsToday() throws Exception {
        RequestPostProcessor link = magicLink(owner, MagicLinkAccessLevel.ONLY_LIVE);
        String yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1).toString();
        String today = LocalDate.now(ZoneOffset.UTC).toString();

        mockMvc.perform(get("/api/v2/locations/metadata/{userId}", owner.getId())
                                .param("start", yesterday).param("end", today)
                                .with(link))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPoints").value(1));
        mockMvc.perform(get("/api/v2/trips/{userId}", owner.getId())
                                .param("startDate", yesterday).param("endDate", today)
                                .with(link))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v2/locations/metadata/{userId}", owner.getId())
                                .param("start", "2024-01-01").param("end", "2024-01-31")
                                .with(link))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v2/locations/stream/{userId}", owner.getId())
                                .param("start", "2024-01-01").param("end", "2024-01-31")
                                .with(link))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v2/trips/{userId}", owner.getId())
                                .param("startDate", "2024-01-01").param("endDate", "2024-01-31")
                                .with(link))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v2/coverage/cells/{userId}", owner.getId())
                                .param("start", "2024-01-01").param("end", "2024-01-31")
                                .with(link))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v2/locations/metadata/{userId}/device/{deviceId}", owner.getId(), testingService.findDefaultDevice(owner).id())
                                .param("start", today).param("end", today)
                                .with(link))
                .andExpect(status().isForbidden());
    }

    @Test
    void lastLocationLinkOnlyReadsTheLatestPoint() throws Exception {
        RequestPostProcessor link = magicLink(owner, MagicLinkAccessLevel.ONLY_LAST_LOCATION);
        String today = LocalDate.now(ZoneOffset.UTC).toString();

        mockMvc.perform(get("/api/v2/locations/metadata/{userId}", owner.getId())
                                .param("start", today).param("end", today)
                                .with(link))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPoints").value(0))
                .andExpect(jsonPath("$.latestLocation.latitude").exists());
        mockMvc.perform(get("/api/v2/locations/stream/{userId}", owner.getId())
                                .param("start", today).param("end", today)
                                .with(link))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v2/trips/{userId}", owner.getId())
                                .param("startDate", today).param("endDate", today)
                                .with(link))
                .andExpect(status().isForbidden());
    }

    @Test
    void memoryLinkOnlyReadsTheRangeOfItsMemory() throws Exception {
        Memory memory = memoryJdbcService.create(owner, new Memory("Trip", "", MEMORY_START, MEMORY_END, HeaderType.MAP, null));
        RequestPostProcessor link = memoryLink(owner, memory, MagicLinkAccessLevel.MEMORY_VIEW_ONLY);

        mockMvc.perform(get("/api/v2/locations/metadata/{userId}", owner.getId())
                                .param("start", MEMORY_START.toString()).param("end", MEMORY_END.toString())
                                .with(link))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPoints").value(1))
                .andExpect(jsonPath("$.latestLocation.latitude").doesNotExist());
        mockMvc.perform(get("/api/v2/locations/metadata/{userId}", owner.getId())
                                .param("start", "2024-01-08").param("end", "2024-01-31")
                                .with(link))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v2/locations/stream/{userId}", owner.getId())
                                .param("start", "2024-01-08").param("end", "2024-01-31")
                                .with(link))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/raw-location-points/{userId}", owner.getId())
                                .param("startDate", "2024-01-01").param("endDate", "2024-01-07")
                                .with(link))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/latest-location").with(link))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v2/trips/{userId}", owner.getId())
                                .param("startDate", "2024-01-01").param("endDate", "2024-01-07")
                                .with(link))
                .andExpect(status().isForbidden());
    }

    @Test
    void memoryLinkOfAnotherMemoryIsRejected() throws Exception {
        Memory foreignMemory = memoryJdbcService.create(other, new Memory("Other", "", MEMORY_START, MEMORY_END, HeaderType.MAP, null));

        mockMvc.perform(get("/api/v2/locations/metadata/{userId}", owner.getId())
                                .param("start", MEMORY_START.toString()).param("end", MEMORY_END.toString())
                                .with(memoryLink(owner, foreignMemory, MagicLinkAccessLevel.MEMORY_VIEW_ONLY)))
                .andExpect(status().isForbidden());
    }

    private void addPoint(User user, Instant timestamp) {
        rawLocationPointJdbcService.create(user, new RawLocationPoint(timestamp, GeoPoint.from(53.5, 9.9), 10.0));
    }

    private void shareWith(User sharingUser, User sharedWith) {
        userSharingJdbcService.create(sharingUser, Set.of(new UserSharing(null, sharingUser.getId(), sharedWith.getId(), null, "#ff0000", false, null)));
    }

    private static RequestPostProcessor magicLink(User owner, MagicLinkAccessLevel level) {
        return asPrincipal(new TokenUser(owner, "test-token", MagicLinkResourceType.MAP, null, List.of(level.asAuthority().getAuthority())));
    }

    private static RequestPostProcessor memoryLink(User owner, Memory memory, MagicLinkAccessLevel level) {
        return asPrincipal(new TokenUser(owner, "test-token", MagicLinkResourceType.MEMORY, memory.getId(), List.of(level.asAuthority().getAuthority())));
    }

    private static RequestPostProcessor asPrincipal(TokenUser tokenUser) {
        return authentication(new UsernamePasswordAuthenticationToken(tokenUser, null, tokenUser.getAuthorities()));
    }
}
