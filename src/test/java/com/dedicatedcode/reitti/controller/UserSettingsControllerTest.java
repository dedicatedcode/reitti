package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.model.UserSettings;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import com.dedicatedcode.reitti.repository.UserSettingsJdbcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
public class UserSettingsControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserJdbcService userJdbcService;

    @Autowired
    private UserSettingsJdbcService userSettingsJdbcService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private String randomUsername() {
        return "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    @Test
    @WithMockUser(username = "testuser")
    void getUsersContent_ShouldReturnUsersFragment() throws Exception {
        mockMvc.perform(get("/settings/users-content"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/user-management :: users-content"))
                .andExpect(model().attributeExists("users"))
                .andExpect(model().attributeExists("currentUsername"))
                .andExpect(model().attribute("currentUsername", "testuser"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void createUser_ShouldCreateNewUser() throws Exception {
        String newUsername = randomUsername();
        String newDisplayName = "New User";
        String newPassword = "password123";

        // Verify user doesn't exist before creation
        Optional<User> userBefore = userJdbcService.findByUsername(newUsername);
        assertThat(userBefore).isEmpty();

        mockMvc.perform(post("/settings/users")
                        .param("username", newUsername)
                        .param("displayName", newDisplayName)
                        .param("password", newPassword)
                        .param("preferred_language", "en")
                        .param("unit_system", "METRIC")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/user-management :: users-content"))
                .andExpect(model().attributeExists("successMessage"))
                .andExpect(model().attributeExists("users"))
                .andExpect(model().attributeExists("currentUsername"));

        // Verify user was created in database
        Optional<User> userAfter = userJdbcService.findByUsername(newUsername);
        assertThat(userAfter).isPresent();
        assertThat(userAfter.get().getUsername()).isEqualTo(newUsername);
        assertThat(userAfter.get().getDisplayName()).isEqualTo(newDisplayName);
    }

    @Test
    @WithMockUser(username = "testuser")
    void createUser_WithInvalidData_ShouldShowError() throws Exception {
        mockMvc.perform(post("/settings/users")
                        .param("username", "")
                        .param("displayName", "")
                        .param("password", "")
                        .param("preferred_language", "en")
                        .param("unit_system", "METRIC")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/user-management :: users-content"))
                .andExpect(model().attributeExists("errorMessage"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void updateUser_ShouldUpdateExistingUser() throws Exception {
        // Create a user first
        String originalUsername = randomUsername();
        String originalDisplayName = "Update User";
        String originalPassword = "password123";
        userJdbcService.createUser(originalUsername, originalDisplayName, originalPassword);

        User createdUser = userJdbcService.findByUsername(originalUsername).orElseThrow();
        Long userId = createdUser.getId();

        String updatedUsername = randomUsername();
        String updatedDisplayName = "Updated User";
        String updatedPassword = "newpassword123";

        mockMvc.perform(post("/settings/users/update")
                        .param("userId", userId.toString())
                        .param("username", updatedUsername)
                        .param("displayName", updatedDisplayName)
                        .param("password", updatedPassword)
                        .param("preferred_language", "en")
                        .param("unit_system", "METRIC")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/user-management :: users-content"))
                .andExpect(model().attributeExists("successMessage"))
                .andExpect(model().attributeExists("users"));

        // Verify user was updated in database
        User updatedUser = userJdbcService.findById(userId).orElseThrow();
        assertThat(updatedUser.getUsername()).isEqualTo(updatedUsername);
        assertThat(updatedUser.getDisplayName()).isEqualTo(updatedDisplayName);
    }

    @Test
    @WithMockUser(username = "admin")
    void deleteUser_ShouldDeleteUser() throws Exception {
        // Create a user first
        String usernameToDelete = randomUsername();
        String displayName = "Delete User";
        String password = "password123";
        userJdbcService.createUser(usernameToDelete, displayName, password);

        User createdUser = userJdbcService.findByUsername(usernameToDelete).orElseThrow();
        Long userId = createdUser.getId();

        // Verify user exists before deletion
        assertThat(userJdbcService.findById(userId)).isPresent();

        mockMvc.perform(post("/settings/users/{userId}/delete", userId)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/user-management :: users-content"))
                .andExpect(model().attributeExists("successMessage"))
                .andExpect(model().attributeExists("users"));

        // Verify user was deleted from database
        assertThat(userJdbcService.findById(userId)).isEmpty();
    }

    @Test
    void deleteUser_SelfDeletion_ShouldShowError() throws Exception {
        // Create the current user
        String currentUsername = randomUsername();
        String displayName = "Test User";
        String password = "password123";
        userJdbcService.createUser(currentUsername, displayName, password);

        User currentUser = userJdbcService.findByUsername(currentUsername).orElseThrow();
        Long userId = currentUser.getId();

        mockMvc.perform(post("/settings/users/{userId}/delete", userId)
                        .with(csrf())
                        .with(user(currentUsername)))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/user-management :: users-content"))
                .andExpect(model().attributeExists("errorMessage"))
                .andExpect(model().attributeExists("users"));

        // Verify user was NOT deleted from database
        assertThat(userJdbcService.findById(userId)).isPresent();
    }

    @Test
    @WithMockUser(username = "testuser")
    void getUserForm_WithoutUserId_ShouldReturnEmptyForm() throws Exception {
        mockMvc.perform(get("/settings/user-form"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/user-management :: user-form"))
                .andExpect(model().attributeDoesNotExist("userId"))
                .andExpect(model().attributeDoesNotExist("username"))
                .andExpect(model().attributeDoesNotExist("displayName"));
    }

    @Test
    @WithMockUser(username = "testuser")
    void getUserForm_WithUserId_ShouldReturnPopulatedForm() throws Exception {
        Long userId = 1L;
        String username = "formuser";
        String displayName = "Form User";

        mockMvc.perform(get("/settings/user-form")
                        .param("userId", userId.toString())
                        .param("username", username)
                        .param("displayName", displayName))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/user-management :: user-form"))
                .andExpect(model().attribute("userId", userId))
                .andExpect(model().attribute("username", username))
                .andExpect(model().attribute("displayName", displayName))
                .andExpect(model().attributeExists("unitSystems"))
                .andExpect(model().attributeExists("selectedUnitSystem"));
    }

    @Test
    void updateUser_CurrentUser_ShouldSetReloginFlag() throws Exception {
        // Create the current user
        String currentUsername = randomUsername();
        String displayName = "Test User";
        String password = "password123";
        userJdbcService.createUser(currentUsername, displayName, password);

        User currentUser = userJdbcService.findByUsername(currentUsername).orElseThrow();
        Long userId = currentUser.getId();

        String newUsername = randomUsername();

        mockMvc.perform(post("/settings/users/update")
                        .param("userId", userId.toString())
                        .param("username", newUsername)
                        .param("displayName", displayName)
                        .param("password", password)
                        .param("preferred_language", "en")
                        .param("unit_system", "METRIC")
                        .with(csrf())
                        .with(user(currentUsername)))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/user-management :: users-content"))
                .andExpect(model().attributeExists("successMessage"))
                .andExpect(model().attribute("requireRelogin", true))
                .andExpect(model().attribute("newUsername", newUsername));

        // Verify user was updated in database
        User updatedUser = userJdbcService.findById(userId).orElseThrow();
        assertThat(updatedUser.getUsername()).isEqualTo(newUsername);
    }

    @Test
    @WithMockUser(username = "testuser")
    void createUser_WithConnectedAccounts_ShouldCreateUserWithConnectedAccounts() throws Exception {
        // Create a user to connect to
        String connectedUsername = randomUsername();
        userJdbcService.createUser(connectedUsername, "Connected User", "password123");
        User connectedUser = userJdbcService.findByUsername(connectedUsername).orElseThrow();

        String newUsername = randomUsername();
        String newDisplayName = "New User";
        String newPassword = "password123";

        mockMvc.perform(post("/settings/users")
                        .param("username", newUsername)
                        .param("displayName", newDisplayName)
                        .param("password", newPassword)
                        .param("preferred_language", "en")
                        .param("unit_system", "METRIC")
                        .param("connectedUserIds", connectedUser.getId().toString())
                        .param("connectedUserColors", "#FF0000")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/user-management :: users-content"))
                .andExpect(model().attributeExists("successMessage"));

        // Verify user was created with connected accounts
        User createdUser = userJdbcService.findByUsername(newUsername).orElseThrow();
        Optional<UserSettings> userSettings = userSettingsJdbcService.findByUserId(createdUser.getId());
        assertThat(userSettings).isPresent();
        assertThat(userSettings.get().getConnectedUserAccounts()).hasSize(1);
        assertThat(userSettings.get().getConnectedUserAccounts().get(0).userId()).isEqualTo(connectedUser.getId());
        assertThat(userSettings.get().getConnectedUserAccounts().get(0).color()).isEqualTo("#FF0000");
    }

    @Test
    @WithMockUser(username = "testuser")
    void updateUser_WithConnectedAccounts_ShouldUpdateConnectedAccounts() throws Exception {
        // Create users
        String originalUsername = randomUsername();
        userJdbcService.createUser(originalUsername, "Original User", "password123");
        User originalUser = userJdbcService.findByUsername(originalUsername).orElseThrow();

        String connectedUsername = randomUsername();
        userJdbcService.createUser(connectedUsername, "Connected User", "password123");
        User connectedUser = userJdbcService.findByUsername(connectedUsername).orElseThrow();

        mockMvc.perform(post("/settings/users/update")
                        .param("userId", originalUser.getId().toString())
                        .param("username", originalUsername)
                        .param("displayName", "Updated User")
                        .param("preferred_language", "en")
                        .param("unit_system", "METRIC")
                        .param("connectedUserIds", connectedUser.getId().toString())
                        .param("connectedUserColors", "#00FF00")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/user-management :: users-content"))
                .andExpect(model().attributeExists("successMessage"));

        // Verify connected accounts were updated
        Optional<UserSettings> userSettings = userSettingsJdbcService.findByUserId(originalUser.getId());
        assertThat(userSettings).isPresent();
        assertThat(userSettings.get().getConnectedUserAccounts()).hasSize(1);
        assertThat(userSettings.get().getConnectedUserAccounts().get(0).userId()).isEqualTo(connectedUser.getId());
        assertThat(userSettings.get().getConnectedUserAccounts().get(0).color()).isEqualTo("#00FF00");
    }

    @Test
    @WithMockUser(username = "testuser")
    void createUser_WithMismatchedConnectedAccountsData_ShouldIgnoreConnectedAccounts() throws Exception {
        String newUsername = randomUsername();
        String newDisplayName = "New User";
        String newPassword = "password123";

        // Mismatched arrays - 2 user IDs but 1 color
        mockMvc.perform(post("/settings/users")
                        .param("username", newUsername)
                        .param("displayName", newDisplayName)
                        .param("password", newPassword)
                        .param("preferred_language", "en")
                        .param("unit_system", "METRIC")
                        .param("connectedUserIds", "1", "2")
                        .param("connectedUserColors", "#FF0000")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/user-management :: users-content"))
                .andExpect(model().attributeExists("successMessage"));

        // Verify user was created but with no connected accounts due to mismatched data
        User createdUser = userJdbcService.findByUsername(newUsername).orElseThrow();
        Optional<UserSettings> userSettings = userSettingsJdbcService.findByUserId(createdUser.getId());
        assertThat(userSettings).isPresent();
        assertThat(userSettings.get().getConnectedUserAccounts()).isEmpty();
    }

    @Test
    @WithMockUser(username = "testuser")
    void getUserForm_WithoutUserId_ShouldIncludeAvailableUsers() throws Exception {
        mockMvc.perform(get("/settings/user-form"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/user-management :: user-form"))
                .andExpect(model().attributeExists("unitSystems"))
                .andExpect(model().attribute("selectedLanguage", "en"))
                .andExpect(model().attribute("selectedUnitSystem", "METRIC"));
    }
}
