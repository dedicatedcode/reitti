package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.memory.*;
import com.dedicatedcode.reitti.model.security.MagicLinkAccessLevel;
import com.dedicatedcode.reitti.model.security.MagicLinkResourceType;
import com.dedicatedcode.reitti.model.security.MagicLinkToken;
import com.dedicatedcode.reitti.model.security.TokenUser;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.MagicLinkJdbcService;
import com.dedicatedcode.reitti.repository.MemoryBlockImageGalleryJdbcService;
import com.dedicatedcode.reitti.repository.MemoryBlockJdbcService;
import com.dedicatedcode.reitti.repository.MemoryBlockTextJdbcService;
import com.dedicatedcode.reitti.service.MagicLinkTokenService;
import com.dedicatedcode.reitti.service.MemoryService;
import com.dedicatedcode.reitti.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Access control of memories, their blocks, their data API and their stored images.
 */
@IntegrationTest
class MemorySecurityTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};
    private static final Instant START = Instant.parse("2023-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2023-01-02T00:00:00Z");

    @Autowired
    private TestingService testingService;
    @Autowired
    private MemoryService memoryService;
    @Autowired
    private MemoryBlockJdbcService memoryBlockJdbcService;
    @Autowired
    private MemoryBlockTextJdbcService memoryBlockTextJdbcService;
    @Autowired
    private MemoryBlockImageGalleryJdbcService memoryBlockImageGalleryJdbcService;
    @Autowired
    private MagicLinkJdbcService magicLinkJdbcService;
    @Autowired
    private MagicLinkTokenService magicLinkTokenService;
    @Autowired
    private StorageService storageService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private User owner;
    private User attacker;
    private Memory ownerMemory;
    private Memory otherOwnerMemory;
    private Memory attackerMemory;
    private MemoryBlock ownerTextBlock;

    @BeforeEach
    void setUp(WebApplicationContext webApplicationContext) {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .defaultRequest(get("/").locale(Locale.US))
                .build();
        owner = testingService.randomUser();
        attacker = testingService.randomUser();
        ownerMemory = createMemory(owner, "Owner memory");
        otherOwnerMemory = createMemory(owner, "Another owner memory");
        attackerMemory = createMemory(attacker, "Attacker memory");
        ownerTextBlock = createTextBlock(owner, ownerMemory, "original headline");
    }

    // --- cross-user block access

    @Test
    void cannotDeleteBlockOfAnotherUsersMemory() throws Exception {
        mockMvc.perform(delete("/memories/{memoryId}/blocks/{blockId}", attackerMemory.getId(), ownerTextBlock.getId())
                        .with(user(attacker)))
                .andExpect(status().isNotFound());

        assertTrue(memoryBlockJdbcService.findById(owner, ownerTextBlock.getId()).isPresent());
    }

    @Test
    void cannotOverwriteTextBlockOfAnotherUsersMemory() throws Exception {
        mockMvc.perform(post("/memories/{memoryId}/blocks/{blockId}/text", attackerMemory.getId(), ownerTextBlock.getId())
                        .param("headline", "pwned")
                        .param("content", "pwned")
                        .with(user(attacker)))
                .andExpect(status().isNotFound());

        assertEquals("original headline", memoryBlockTextJdbcService.findByBlockId(ownerTextBlock.getId()).orElseThrow().getHeadline());
    }

    @Test
    void cannotOverwriteImageGalleryOfAnotherUsersMemory() throws Exception {
        MemoryBlock gallery = memoryService.addBlock(owner, ownerMemory.getId(), -1, BlockType.IMAGE_GALLERY);
        memoryService.addImageGalleryBlock(gallery.getId(), List.of(new MemoryBlockImageGallery.GalleryImage("/original.png", null, "upload", null)));

        mockMvc.perform(post("/memories/{memoryId}/blocks/{blockId}/image-gallery", attackerMemory.getId(), gallery.getId())
                        .param("uploadedUrls", "/pwned.png")
                        .with(user(attacker)))
                .andExpect(status().isNotFound());

        assertEquals("/original.png", memoryBlockImageGalleryJdbcService.findByBlockId(gallery.getId()).orElseThrow().getImages().getFirst().getImageUrl());
    }

    @Test
    void cannotCopyAnotherUsersTripsOrVisitsIntoOwnMemory() throws Exception {
        Trip ownerTrip = createTrip(owner);

        mockMvc.perform(post("/memories/{memoryId}/blocks/cluster", attackerMemory.getId())
                        .param("selectedParts", String.valueOf(ownerTrip.getId()))
                        .param("type", "CLUSTER_TRIP")
                        .with(user(attacker)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/memories/{memoryId}/blocks/cluster", attackerMemory.getId())
                        .param("selectedParts", String.valueOf(ownerTrip.getStartVisit().getId()))
                        .param("type", "CLUSTER_VISIT")
                        .with(user(attacker)))
                .andExpect(status().isNotFound());

        Trip attackerTrip = createTrip(attacker);
        MemoryClusterBlock attackerCluster = memoryService.createClusterBlock(attacker, attackerMemory, "mine", -1, BlockType.CLUSTER_TRIP, List.of(attackerTrip.getId()));
        mockMvc.perform(post("/memories/{memoryId}/blocks/{blockId}/cluster", attackerMemory.getId(), attackerCluster.getBlockId())
                        .param("selectedParts", "t-id:" + ownerTrip.getId())
                        .with(user(attacker)))
                .andExpect(status().isNotFound());

        assertEquals(0, countCopiedRows(attacker, "memory_trips", ownerTrip.getId()));
        assertEquals(0, countCopiedRows(attacker, "memory_visits", ownerTrip.getStartVisit().getId()));
        // only the attacker's own block remains, the rejected one was rolled back
        assertEquals(1, memoryBlockJdbcService.findByMemoryId(attackerMemory.getId()).size());
    }

    @Test
    void ownerCanStillBuildClusterBlocksFromOwnTrips() throws Exception {
        Trip ownerTrip = createTrip(owner);

        mockMvc.perform(post("/memories/{memoryId}/blocks/cluster", ownerMemory.getId())
                        .param("selectedParts", String.valueOf(ownerTrip.getId()))
                        .param("type", "CLUSTER_TRIP")
                        .with(user(owner)))
                .andExpect(status().isOk());

        assertEquals(1, countCopiedRows(owner, "memory_trips", ownerTrip.getId()));
    }

    // --- magic link scope

    @Test
    void viewOnlyLinkCannotCreateShareLinks() throws Exception {
        String rawToken = magicLinkTokenService.createMemoryShareToken(owner, ownerMemory.getId(), MagicLinkAccessLevel.MEMORY_VIEW_ONLY, 30);
        MockHttpSession linkSession = openMagicLink(rawToken, ownerMemory.getId());

        mockMvc.perform(get("/memories/{id}/share", ownerMemory.getId()).session(linkSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/memories/{id}/share/form", ownerMemory.getId()).param("accessLevel", "MEMORY_EDIT_ACCESS").session(linkSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/memories/{id}/share", ownerMemory.getId())
                        .param("accessLevel", "FULL_ACCESS")
                        .param("validDays", "0")
                        .session(linkSession))
                .andExpect(status().isForbidden());

        assertEquals(1, magicLinkJdbcService.findByUser(owner).size());
    }

    @Test
    void editLinkCannotCreateShareLinks() throws Exception {
        mockMvc.perform(post("/memories/{id}/share", ownerMemory.getId())
                        .param("accessLevel", "MEMORY_VIEW_ONLY")
                        .with(memoryLink(MagicLinkAccessLevel.MEMORY_EDIT_ACCESS, ownerMemory.getId())))
                .andExpect(status().isForbidden());

        assertTrue(magicLinkJdbcService.findByUser(owner).isEmpty());
    }

    @Test
    void ownerCanOnlyShareMemoryAccessLevels() throws Exception {
        mockMvc.perform(post("/memories/{id}/share", ownerMemory.getId())
                        .param("accessLevel", "FULL_ACCESS")
                        .with(user(owner)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/memories/{id}/share/form", ownerMemory.getId())
                        .param("accessLevel", "ONLY_LIVE")
                        .with(user(owner)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/memories/{id}/share", ownerMemory.getId())
                        .param("accessLevel", "MEMORY_VIEW_ONLY")
                        .param("validDays", "-1")
                        .with(user(owner)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/memories/{id}/share", attackerMemory.getId())
                        .param("accessLevel", "MEMORY_VIEW_ONLY")
                        .with(user(owner)))
                .andExpect(status().isNotFound());
        assertTrue(magicLinkJdbcService.findByUser(owner).isEmpty());

        mockMvc.perform(post("/memories/{id}/share", ownerMemory.getId())
                        .param("accessLevel", "MEMORY_VIEW_ONLY")
                        .param("validDays", "7")
                        .with(user(owner)))
                .andExpect(status().isOk());

        List<MagicLinkToken> tokens = magicLinkJdbcService.findByUser(owner);
        assertEquals(1, tokens.size());
        assertEquals(MagicLinkAccessLevel.MEMORY_VIEW_ONLY, tokens.getFirst().getAccessLevel());
        assertEquals(ownerMemory.getId(), tokens.getFirst().getResourceId());
        assertNotNull(tokens.getFirst().getExpiryDate());
    }

    @Test
    void viewOnlyLinkCannotModifyTheMemory() throws Exception {
        RequestPostProcessor viewLink = memoryLink(MagicLinkAccessLevel.MEMORY_VIEW_ONLY, ownerMemory.getId());

        mockMvc.perform(get("/memories/{id}", ownerMemory.getId()).with(viewLink))
                .andExpect(status().isOk());
        mockMvc.perform(post("/memories/{id}/blocks/text", ownerMemory.getId())
                        .param("headline", "added").with(viewLink))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/memories/{id}/blocks/{blockId}/text", ownerMemory.getId(), ownerTextBlock.getId())
                        .param("headline", "pwned").param("content", "pwned").with(viewLink))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/memories/{id}/blocks/{blockId}", ownerMemory.getId(), ownerTextBlock.getId()).with(viewLink))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/memories/{id}/blocks/reorder", ownerMemory.getId())
                        .param("blockIds", String.valueOf(ownerTextBlock.getId())).with(viewLink))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/memories/{id}/blocks/upload-image", ownerMemory.getId())
                        .file(new MockMultipartFile("files", "a.png", "image/png", PNG)).with(viewLink))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/memories/{id}/blocks/new", ownerMemory.getId())
                        .param("type", "TRIP_CLUSTER").with(viewLink))
                .andExpect(status().isForbidden());

        assertEquals(1, memoryBlockJdbcService.findByMemoryId(ownerMemory.getId()).size());
        assertEquals("original headline", memoryBlockTextJdbcService.findByBlockId(ownerTextBlock.getId()).orElseThrow().getHeadline());
    }

    @Test
    void editLinkIsLimitedToItsOwnMemory() throws Exception {
        RequestPostProcessor editLink = memoryLink(MagicLinkAccessLevel.MEMORY_EDIT_ACCESS, ownerMemory.getId());
        MemoryBlock blockOfOtherMemory = createTextBlock(owner, otherOwnerMemory, "other headline");

        mockMvc.perform(post("/memories/{id}/blocks/{blockId}/text", ownerMemory.getId(), ownerTextBlock.getId())
                        .param("headline", "edited").param("content", "edited").with(editLink))
                .andExpect(status().isOk());
        assertEquals("edited", memoryBlockTextJdbcService.findByBlockId(ownerTextBlock.getId()).orElseThrow().getHeadline());

        mockMvc.perform(get("/memories/{id}", otherOwnerMemory.getId()).with(editLink))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/memories/{id}/blocks/text", otherOwnerMemory.getId())
                        .param("headline", "added").with(editLink))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/memories/{id}/blocks/{blockId}/text", ownerMemory.getId(), blockOfOtherMemory.getId())
                        .param("headline", "pwned").param("content", "pwned").with(editLink))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/memories/{id}/blocks/{blockId}", ownerMemory.getId(), blockOfOtherMemory.getId()).with(editLink))
                .andExpect(status().isNotFound());

        assertEquals("other headline", memoryBlockTextJdbcService.findByBlockId(blockOfOtherMemory.getId()).orElseThrow().getHeadline());
    }

    @Test
    void memoryLinkWithoutBoundMemoryGrantsNothing() throws Exception {
        RequestPostProcessor unboundLink = memoryLink(MagicLinkAccessLevel.MEMORY_EDIT_ACCESS, null);

        mockMvc.perform(get("/memories/{id}", ownerMemory.getId()).with(unboundLink))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/memories/{id}/blocks/{blockId}/text", ownerMemory.getId(), ownerTextBlock.getId())
                        .param("headline", "pwned").param("content", "pwned").with(unboundLink))
                .andExpect(status().isNotFound());
        assertEquals("original headline", memoryBlockTextJdbcService.findByBlockId(ownerTextBlock.getId()).orElseThrow().getHeadline());
    }

    @Test
    void memoryLinkCannotListTheOwnersMemories() throws Exception {
        RequestPostProcessor viewLink = memoryLink(MagicLinkAccessLevel.MEMORY_VIEW_ONLY, ownerMemory.getId());

        mockMvc.perform(get("/memories/all").with(viewLink)).andExpect(status().isForbidden());
        mockMvc.perform(get("/memories/year/{year}", 2023).with(viewLink)).andExpect(status().isForbidden());
        mockMvc.perform(get("/memories/years-navigation").with(viewLink)).andExpect(status().isForbidden());
        mockMvc.perform(get("/memories/{id}", otherOwnerMemory.getId()).with(viewLink)).andExpect(status().isNotFound());

        mockMvc.perform(get("/memories/all").with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("memories", hasSize(2)));
    }

    // --- memory data API

    @Test
    void memoryDataApiOnlyServesAccessibleBlocks() throws Exception {
        Trip ownerTrip = createTrip(owner);
        MemoryClusterBlock cluster = memoryService.createClusterBlock(owner, ownerMemory, "trips", -1, BlockType.CLUSTER_TRIP, List.of(ownerTrip.getId()));
        String tripsUrl = "/api/v2/memories/trips/{memoryId}/{blockId}";
        String visitsUrl = "/api/v2/memories/visits/{memoryId}/{blockId}";

        mockMvc.perform(get(tripsUrl, ownerMemory.getId(), cluster.getBlockId()).with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get(tripsUrl, ownerMemory.getId(), cluster.getBlockId())
                        .with(memoryLink(MagicLinkAccessLevel.MEMORY_VIEW_ONLY, ownerMemory.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get(tripsUrl, ownerMemory.getId(), cluster.getBlockId()).with(user(attacker)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(visitsUrl, ownerMemory.getId(), cluster.getBlockId()).with(user(attacker)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(tripsUrl, attackerMemory.getId(), cluster.getBlockId()).with(user(attacker)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(tripsUrl, otherOwnerMemory.getId(), cluster.getBlockId())
                        .with(memoryLink(MagicLinkAccessLevel.MEMORY_VIEW_ONLY, otherOwnerMemory.getId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(tripsUrl, ownerMemory.getId(), cluster.getBlockId())
                        .with(memoryLink(MagicLinkAccessLevel.MEMORY_VIEW_ONLY, otherOwnerMemory.getId())))
                .andExpect(status().isNotFound());
        // map links are already refused by the route rules in SecurityConfig
        mockMvc.perform(get(tripsUrl, ownerMemory.getId(), cluster.getBlockId()).with(mapLink(MagicLinkAccessLevel.FULL_ACCESS)))
                .andExpect(status().isForbidden());
    }

    // --- images

    @Test
    void uploadsOnlyAcceptRealImagesAndIgnoreTheClientExtension() throws Exception {
        mockMvc.perform(multipart("/memories/{id}/blocks/upload-image", ownerMemory.getId())
                        .file(new MockMultipartFile("files", "x.html", "text/html", "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8)))
                        .with(user(owner)))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(multipart("/memories/{id}/blocks/upload-image", ownerMemory.getId())
                        .file(new MockMultipartFile("files", "x.svg", "image/svg+xml", "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>".getBytes(StandardCharsets.UTF_8)))
                        .with(user(owner)))
                .andExpect(status().isUnsupportedMediaType());

        String url = uploadPng(owner, ownerMemory, "evil.html");
        assertTrue(url.endsWith(".png"), url);
    }

    @Test
    void memoryImagesAreOnlyServedToThoseWhoCanSeeTheMemory() throws Exception {
        String url = uploadPng(owner, ownerMemory, "photo.png");

        mockMvc.perform(get(url).with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", containsString("sandbox")));
        mockMvc.perform(get(url).with(memoryLink(MagicLinkAccessLevel.MEMORY_VIEW_ONLY, ownerMemory.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(get(url).with(user(attacker))).andExpect(status().isNotFound());
        mockMvc.perform(get(url).with(memoryLink(MagicLinkAccessLevel.MEMORY_VIEW_ONLY, otherOwnerMemory.getId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(url).with(mapLink(MagicLinkAccessLevel.FULL_ACCESS))).andExpect(status().isNotFound());
    }

    @Test
    void storedNonImageFilesAreNeverServed() throws Exception {
        String directory = "memories/" + ownerMemory.getId() + "/";
        storageService.store(directory + "planted.html", new ByteArrayInputStream("<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)), 25, "text/html");
        storageService.store(directory + "planted.svg", new ByteArrayInputStream("<svg onload=\"alert(1)\"/>".getBytes(StandardCharsets.UTF_8)), 24, "image/svg+xml");

        mockMvc.perform(get("/api/v1/photos/reitti/memories/{id}/planted.html", ownerMemory.getId()).with(user(owner)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/photos/reitti/memories/{id}/planted.svg", ownerMemory.getId()).with(user(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void immichAssetIdMustBeAUuid() throws Exception {
        mockMvc.perform(post("/memories/{id}/blocks/fetch-immich-photo", ownerMemory.getId())
                        .param("assetId", "../../../../tmp/pwned")
                        .with(user(owner)))
                .andExpect(status().isBadRequest());
    }

    // --- helpers

    private Memory createMemory(User user, String title) {
        return memoryService.createMemory(user, new Memory(title, null, START, END, HeaderType.MAP, null));
    }

    private MemoryBlock createTextBlock(User user, Memory memory, String headline) {
        MemoryBlock block = memoryService.addBlock(user, memory.getId(), -1, BlockType.TEXT);
        memoryService.addTextBlock(block.getId(), headline, "content");
        return block;
    }

    private Trip createTrip(User user) {
        SignificantPlace place = testingService.newSignificantPlace(user);
        ProcessedVisit start = testingService.createVisit(user, place, START.plusSeconds(3600), START.plusSeconds(7200));
        ProcessedVisit end = testingService.createVisit(user, place, START.plusSeconds(10800), START.plusSeconds(14400));
        return testingService.createTrip(user, start, end);
    }

    private int countCopiedRows(User user, String table, Long originalId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id = ? AND original_id = ?", Integer.class, user.getId(), originalId);
        return count != null ? count : 0;
    }

    private String uploadPng(User user, Memory memory, String clientFilename) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/memories/{id}/blocks/upload-image", memory.getId())
                        .file(new MockMultipartFile("files", clientFilename, "text/html", PNG))
                        .with(user(user)))
                .andExpect(status().isOk())
                .andReturn();
        @SuppressWarnings("unchecked")
        List<String> urls = (List<String>) result.getModelAndView().getModel().get("urls");
        assertEquals(1, urls.size());
        return urls.getFirst();
    }

    private MockHttpSession openMagicLink(String rawToken, Long memoryId) throws Exception {
        MvcResult result = mockMvc.perform(get("/memories/{id}", memoryId).param("mt", rawToken))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private RequestPostProcessor memoryLink(MagicLinkAccessLevel accessLevel, Long memoryId) {
        return tokenUser(new TokenUser(owner, "test-token", MagicLinkResourceType.MEMORY, memoryId, List.of(accessLevel.asAuthority().getAuthority())));
    }

    private RequestPostProcessor mapLink(MagicLinkAccessLevel accessLevel) {
        return tokenUser(new TokenUser(owner, "test-token", MagicLinkResourceType.MAP, null, List.of(accessLevel.asAuthority().getAuthority())));
    }

    private static RequestPostProcessor tokenUser(TokenUser tokenUser) {
        return authentication(new UsernamePasswordAuthenticationToken(tokenUser, null, tokenUser.getAuthorities()));
    }
}
