package com.dedicatedcode.reitti.controller.settings;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.security.MagicLinkAccessLevel;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSharing;
import com.dedicatedcode.reitti.repository.UserSharingJdbcService;
import com.dedicatedcode.reitti.service.MagicLinkTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@IntegrationTest
class ShareAccessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestingService testingService;

    @Autowired
    private UserSharingJdbcService userSharingJdbcService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MagicLinkTokenService magicLinkTokenService;

    @AfterEach
    void tearDown() {
        testingService.clearData();
        SecurityContextHolder.clearContext();
    }

    private void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @Test
    void shouldCreateShareWithPhotosEnabled() throws Exception {
        User sharer = testingService.randomUser();
        User recipient = testingService.randomUser();
        authenticate(sharer);

        mockMvc.perform(post("/settings/share-access/users")
                        .param("sharedUserIds", recipient.getId().toString())
                        .param("photoUserIds", recipient.getId().toString())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("settings/share-access :: share-with-content"));

        List<UserSharing> shares = userSharingJdbcService.findBySharingUser(sharer.getId());
        assertThat(shares).hasSize(1);
        assertThat(shares.get(0).getSharedWithUserId()).isEqualTo(recipient.getId());
        assertThat(shares.get(0).isSharePhotos()).isTrue();
    }

    @Test
    void shouldCreateShareWithoutPhotosByDefault() throws Exception {
        User sharer = testingService.randomUser();
        User recipient = testingService.randomUser();
        authenticate(sharer);

        mockMvc.perform(post("/settings/share-access/users")
                        .param("sharedUserIds", recipient.getId().toString())
                        .with(csrf()))
                .andExpect(status().isOk());

        List<UserSharing> shares = userSharingJdbcService.findBySharingUser(sharer.getId());
        assertThat(shares).hasSize(1);
        assertThat(shares.get(0).isSharePhotos()).isFalse();
    }

    @Test
    void shouldUpdateSharePhotosFlagOnExistingShare() throws Exception {
        User sharer = testingService.randomUser();
        User recipient = testingService.randomUser();
        authenticate(sharer);

        mockMvc.perform(post("/settings/share-access/users")
                        .param("sharedUserIds", recipient.getId().toString())
                        .with(csrf()))
                .andExpect(status().isOk());

        // Enable photo sharing on the existing share
        mockMvc.perform(post("/settings/share-access/users")
                        .param("sharedUserIds", recipient.getId().toString())
                        .param("photoUserIds", recipient.getId().toString())
                        .with(csrf()))
                .andExpect(status().isOk());

        List<UserSharing> shares = userSharingJdbcService.findBySharingUser(sharer.getId());
        assertThat(shares).hasSize(1);
        assertThat(shares.get(0).isSharePhotos()).isTrue();

        // Disable photo sharing again
        mockMvc.perform(post("/settings/share-access/users")
                        .param("sharedUserIds", recipient.getId().toString())
                        .with(csrf()))
                .andExpect(status().isOk());

        shares = userSharingJdbcService.findBySharingUser(sharer.getId());
        assertThat(shares).hasSize(1);
        assertThat(shares.get(0).isSharePhotos()).isFalse();
    }

    @Test
    void shouldIgnorePhotoFlagForUnsharedUsers() throws Exception {
        User sharer = testingService.randomUser();
        User recipient = testingService.randomUser();
        authenticate(sharer);

        mockMvc.perform(post("/settings/share-access/users")
                        .param("photoUserIds", recipient.getId().toString())
                        .with(csrf()))
                .andExpect(status().isOk());

        List<UserSharing> shares = userSharingJdbcService.findBySharingUser(sharer.getId());
        assertThat(shares).isEmpty();
    }

    @Test
    void shouldDeleteShareWhenUserUnchecked() throws Exception {
        User sharer = testingService.randomUser();
        User recipient = testingService.randomUser();
        authenticate(sharer);

        mockMvc.perform(post("/settings/share-access/users")
                        .param("sharedUserIds", recipient.getId().toString())
                        .param("photoUserIds", recipient.getId().toString())
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/settings/share-access/users")
                        .with(csrf()))
                .andExpect(status().isOk());

        assertThat(userSharingJdbcService.findBySharingUser(sharer.getId())).isEmpty();
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_sharing WHERE sharing_user_id = ?", Long.class, sharer.getId());
        assertThat(count).isZero();
    }

    @Test
    void shouldOnlyDeleteOwnMagicLinks() throws Exception {
        User owner = testingService.randomUser();
        User intruder = testingService.randomUser();
        magicLinkTokenService.createMapShareToken(owner, "owner link", MagicLinkAccessLevel.FULL_ACCESS, null);
        long tokenId = magicLinkTokenService.getTokensForUser(owner).getFirst().getId();

        mockMvc.perform(post("/settings/share-access/magic-links/{id}/delete", tokenId).with(user(intruder)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("errorMessage"));
        assertThat(magicLinkTokenService.getTokensForUser(owner)).hasSize(1);

        mockMvc.perform(post("/settings/share-access/magic-links/{id}/delete", tokenId).with(user(owner)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("successMessage"));
        assertThat(magicLinkTokenService.getTokensForUser(owner)).isEmpty();
    }

    @Test
    void shouldRejectSharingColorsThatAreNoHexColors() throws Exception {
        User sharer = testingService.randomUser();
        User recipient = testingService.randomUser();
        userSharingJdbcService.create(sharer, Set.of(new UserSharing(null, sharer.getId(), recipient.getId(), null, "#ff0000", false, null)));
        long sharingId = userSharingJdbcService.findBySharedWithUser(recipient.getId()).getFirst().getId();
        authenticate(recipient);

        mockMvc.perform(post("/settings/share-access/shared-with-me/{id}/color", sharingId)
                                .param("color", "red\" onmouseover=\"alert(1)")
                                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string("error"));
        assertThat(userSharingJdbcService.findBySharedWithUser(recipient.getId()).getFirst().getColor()).isEqualTo("#ff0000");

        mockMvc.perform(post("/settings/share-access/shared-with-me/{id}/color", sharingId)
                                .param("color", "#00ff00")
                                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string("success"));
        assertThat(userSharingJdbcService.findBySharedWithUser(recipient.getId()).getFirst().getColor()).isEqualTo("#00ff00");
    }
}
