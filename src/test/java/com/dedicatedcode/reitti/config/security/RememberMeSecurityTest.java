package com.dedicatedcode.reitti.config.security;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Remember-me cookies are signed with hash(username:expiry:password:key). Accounts created through OIDC have an
 * empty password, so with a publicly known key anyone could mint a valid cookie for them.
 */
@IntegrationTest
class RememberMeSecurityTest {

    private static final String FORMER_HARDCODED_KEY = "uniqueAndSecretKey";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestingService testingService;

    @Autowired
    private UserJdbcService userJdbcService;

    @Autowired
    private RememberMeKeyProvider rememberMeKeyProvider;

    private User oidcLikeUser;

    @BeforeEach
    void setUp() {
        User user = testingService.randomUser();
        oidcLikeUser = userJdbcService.updateUser(user.withPassword(""));
    }

    @Test
    void keyIsNotTheFormerPublicConstant() {
        assertThat(rememberMeKeyProvider.getKey()).isNotEqualTo(FORMER_HARDCODED_KEY).hasSizeGreaterThanOrEqualTo(32);
    }

    @Test
    void cookieForgedWithPublicKeyIsRejected() throws Exception {
        Cookie forged = cookie(oidcLikeUser.getUsername(), "", FORMER_HARDCODED_KEY);
        mockMvc.perform(get("/settings/api-tokens").cookie(forged))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void cookieSignedWithInstallationKeyIsAccepted() throws Exception {
        // sanity check that the forged cookie above is well-formed and only fails because of the key
        Cookie valid = cookie(oidcLikeUser.getUsername(), "", rememberMeKeyProvider.getKey());
        mockMvc.perform(get("/settings/api-tokens").cookie(valid))
                .andExpect(status().isOk());
    }

    private static Cookie cookie(String username, String password, String key) throws Exception {
        long expiry = System.currentTimeMillis() + 3_600_000;
        String data = username + ":" + expiry + ":" + password + ":" + key;
        String signature = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8)));
        String raw = username + ":" + expiry + ":SHA256:" + signature;
        String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)).replaceAll("=+$", "");
        return new Cookie("remember-me", encoded);
    }
}
