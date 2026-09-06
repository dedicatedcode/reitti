package com.dedicatedcode.reitti.controller.api;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.dto.ImmichSearchResponse;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSharing;
import com.dedicatedcode.reitti.repository.UserSharingJdbcService;
import com.dedicatedcode.reitti.service.integration.ImmichIntegrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
class ImmichPhotoApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestingService testingService;

    @Autowired
    private ImmichIntegrationService immichIntegrationService;

    @Autowired
    private UserSharingJdbcService userSharingJdbcService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private MockRestServiceServer mockServer;

    private User owner;
    private User viewer;

    private static final String IMMICH_BASE_URL = "http://localhost:8089";

    @BeforeEach
    void setUp() {
        mockServer = MockRestServiceServer.createServer(restTemplate);
        owner = testingService.randomUser();
        viewer = testingService.randomUser();
        immichIntegrationService.saveIntegration(owner, IMMICH_BASE_URL, "owner-token", null, null, false, true);
    }

    @AfterEach
    void tearDown() {
        testingService.clearData();
        SecurityContextHolder.clearContext();
    }

    private void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private void shareWith(User sharingUser, User sharedWithUser, boolean sharePhotos) {
        userSharingJdbcService.create(sharingUser, Set.of(new UserSharing(null, sharingUser.getId(), sharedWithUser.getId(), null, "#FF0000", sharePhotos, null)));
    }

    @Test
    void shouldRejectPhotosOfOtherUserWithoutShare() throws Exception {
        authenticate(viewer);

        mockMvc.perform(get("/api/v1/photos/immich/range")
                        .param("startDate", "2024-01-01")
                        .param("endDate", "2024-01-02")
                        .param("timezone", "UTC")
                        .param("userId", owner.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectPhotosOfOtherUserWhenSharePhotosDisabled() throws Exception {
        shareWith(owner, viewer, false);
        authenticate(viewer);

        mockMvc.perform(get("/api/v1/photos/immich/range")
                        .param("startDate", "2024-01-01")
                        .param("endDate", "2024-01-02")
                        .param("timezone", "UTC")
                        .param("userId", owner.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnSharedPhotosWhenSharePhotosEnabled() throws Exception {
        shareWith(owner, viewer, true);
        authenticate(viewer);

        ImmichSearchResponse searchResponse = new ImmichSearchResponse();
        ImmichSearchResponse.AssetsResult assetsResult = new ImmichSearchResponse.AssetsResult();
        com.dedicatedcode.reitti.dto.ImmichAsset asset = new com.dedicatedcode.reitti.dto.ImmichAsset();
        asset.setId("photo-1");
        asset.setOriginalFileName("test.jpg");
        asset.setLocalDateTime("2024-01-01T12:00:00Z");
        com.dedicatedcode.reitti.dto.ImmichAsset.ExifInfo exifInfo = new com.dedicatedcode.reitti.dto.ImmichAsset.ExifInfo();
        exifInfo.setLatitude(40.7128);
        exifInfo.setLongitude(-74.0060);
        exifInfo.setDateTimeOriginal("2024-01-01T12:00:00Z");
        asset.setExifInfo(exifInfo);
        assetsResult.setItems(List.of(asset));
        assetsResult.setTotal(1);
        searchResponse.setAssets(assetsResult);

        mockServer.expect(requestTo(IMMICH_BASE_URL + "/api/search/metadata"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "owner-token"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(searchResponse), MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/v1/photos/immich/range")
                        .param("startDate", "2024-01-01")
                        .param("endDate", "2024-01-02")
                        .param("timezone", "UTC")
                        .param("userId", owner.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("photo-1"))
                .andExpect(jsonPath("$[0].shared").value(true))
                .andExpect(jsonPath("$[0].thumbnailUrl").value("/api/v1/photos/immich/proxy/photo-1/thumbnail?userId=" + owner.getId()));

        mockServer.verify();
    }

    @Test
    void shouldUseOwnIntegrationWhenNoUserIdGiven() throws Exception {
        authenticate(viewer);

        mockMvc.perform(get("/api/v1/photos/immich/range")
                        .param("startDate", "2024-01-01")
                        .param("endDate", "2024-01-02")
                        .param("timezone", "UTC"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void shouldProxySharedPhotoThumbnail() throws Exception {
        shareWith(owner, viewer, true);
        authenticate(viewer);

        byte[] imageData = new byte[]{1, 2, 3, 4, 5};
        mockServer.expect(requestTo(IMMICH_BASE_URL + "/api/assets/photo-1/thumbnail?size=thumbnail"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("x-api-key", "owner-token"))
                .andRespond(withSuccess(imageData, MediaType.IMAGE_JPEG));

        mockMvc.perform(get("/api/v1/photos/immich/proxy/photo-1/thumbnail")
                        .param("userId", owner.getId().toString()))
                .andExpect(status().isOk());

        mockServer.verify();
    }

    @Test
    void shouldRejectSharedPhotoProxyWithoutShare() throws Exception {
        authenticate(viewer);

        mockMvc.perform(get("/api/v1/photos/immich/proxy/photo-1/thumbnail")
                        .param("userId", owner.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldPersistSharePhotosFlag() {
        shareWith(owner, viewer, true);

        List<UserSharing> shares = userSharingJdbcService.findBySharingUser(owner.getId());
        assertThat(shares).hasSize(1);
        assertThat(shares.get(0).getSharedWithUserId()).isEqualTo(viewer.getId());
        assertThat(shares.get(0).isSharePhotos()).isTrue();
    }
}
