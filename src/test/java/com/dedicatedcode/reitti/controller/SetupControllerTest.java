package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.model.Language;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.TimeDisplayMode;
import com.dedicatedcode.reitti.model.TimeMode;
import com.dedicatedcode.reitti.model.UnitSystem;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
public class SetupControllerTest {

    private static final String ADMIN_USERNAME = "admin";
    private static final String MIGRATED_ADMIN_DISPLAY_NAME = "Administrator";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserJdbcService userJdbcService;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void tearDown() {
        // restore the admin password set by V999__reset_admin_password.sql, else the SetupFilter
        // will redirect all requests of subsequent tests to the setup page
        userJdbcService.findByUsername(ADMIN_USERNAME).ifPresent(
                admin -> userJdbcService.updateUser(admin.withPassword(passwordEncoder.encode("admin"))));
    }

    private User adminWithRawPassword(String rawPassword) {
        User admin = userJdbcService.findByUsername(ADMIN_USERNAME).orElseThrow();
        String encoded = rawPassword == null || rawPassword.isEmpty() ? "" : passwordEncoder.encode(rawPassword);
        return userJdbcService.updateUser(admin.withPassword(encoded));
    }

    private User createRegularUser(String password) {
        String username = "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return userService.createNewUser(username, "Regular User", password, Role.USER, UnitSystem.METRIC,
                Language.EN, null, null, null, TimeDisplayMode.DEFAULT, TimeMode.TWENTY_FOUR_HOUR, "#e2e2e2");
    }

    @Test
    void postSetup_WhileAdminPasswordEmpty_ShouldSetPasswordAndAllowLogin() throws Exception {
        adminWithRawPassword(null);

        mockMvc.perform(post("/setup")
                        .param("username", ADMIN_USERNAME)
                        .param("password", "new-password-123")
                        .param("displayName", "Admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        User admin = userJdbcService.findByUsername(ADMIN_USERNAME).orElseThrow();
        assertThat(passwordEncoder.matches("new-password-123", admin.getPassword())).isTrue();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);

        mockMvc.perform(post("/login")
                        .param("username", ADMIN_USERNAME)
                        .param("password", "new-password-123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withUsername(ADMIN_USERNAME));
    }

    @Test
    void postLogin_WithEmptyPassword_ShouldNotAuthenticate() throws Exception {
        adminWithRawPassword(null);

        mockMvc.perform(post("/login")
                        .param("username", ADMIN_USERNAME)
                        .param("password", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(unauthenticated());

        mockMvc.perform(post("/login")
                        .param("username", ADMIN_USERNAME)
                        .param("password", "any-guessed-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(unauthenticated());
    }

    @Test
    void postSetup_AfterSetupCompleted_ShouldNotChangePassword() throws Exception {
        adminWithRawPassword("original-password");

        mockMvc.perform(post("/setup")
                        .param("username", ADMIN_USERNAME)
                        .param("password", "attacker-password")
                        .param("displayName", "Hacked"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        User admin = userJdbcService.findByUsername(ADMIN_USERNAME).orElseThrow();
        assertThat(passwordEncoder.matches("original-password", admin.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("attacker-password", admin.getPassword())).isFalse();
        assertThat(admin.getDisplayName()).isEqualTo(MIGRATED_ADMIN_DISPLAY_NAME);
    }

    @Test
    void postSetup_WithOtherUsersUsername_ShouldNotChangeThatUser() throws Exception {
        adminWithRawPassword(null);
        User regularUser = createRegularUser("users-password");

        mockMvc.perform(post("/setup")
                        .param("username", regularUser.getUsername())
                        .param("password", "attacker-password")
                        .param("displayName", "Hacked"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        User unchanged = userJdbcService.findByUsername(regularUser.getUsername()).orElseThrow();
        assertThat(passwordEncoder.matches("users-password", unchanged.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("attacker-password", unchanged.getPassword())).isFalse();
        assertThat(unchanged.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void getSetup_AfterSetupCompleted_ShouldRedirectToLogin() throws Exception {
        adminWithRawPassword("original-password");

        mockMvc.perform(get("/setup"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
