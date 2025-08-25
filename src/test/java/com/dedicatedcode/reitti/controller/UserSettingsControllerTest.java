package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.repository.UserJdbcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
public class UserSettingsControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserJdbcService userJdbcService;

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
    void getUsersContent_AsRegularUser_ShouldReturnUserForm() throws Exception {
        // Create the test user as a regular user
        String testUsername = randomUsername();
        userJdbcService.createUser(testUsername, "Test User", "password");
        
        mockMvc.perform(get("/settings/users-content")
                        .with(user(testUsername)))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/user-management :: user-form-page"))
                .andExpect(model().attributeExists("userId"))
                .andExpect(model().attributeExists("username"))
                .andExpect(model().attribute("username", testUsername))
                .andExpect(model().attribute("isAdmin", false));
    }

    @Test
    void getUsersContent_AsAdmin_ShouldReturnUsersList() throws Exception {
        // Create the admin user
        String adminUsername = randomUsername();
        User adminUser = userJdbcService.createUser(adminUsername, "Admin User", "password");
        adminUser = adminUser.withRole(Role.ADMIN);
        userJdbcService.updateUser(adminUser);
        
        mockMvc.perform(get("/settings/users-content")
                        .with(user(adminUsername)))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/user-management :: users-list"))
                .andExpect(model().attributeExists("users"))
                .andExpect(model().attributeExists("currentUsername"))
                .andExpect(model().attribute("currentUsername", adminUsername))
                .andExpect(model().attribute("isAdmin", true));
    }

    @Test
    void createUser_AsAdmin_ShouldCreateNewUser() throws Exception {
        // Create the admin user
        String adminUsername = randomUsername();
        User adminUser = userJdbcService.createUser(adminUsername, "Admin User", "password");
        adminUser = adminUser.withRole(Role.ADMIN);
        userJdbcService.updateUser(adminUser);
        
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
                        .param("role", "USER")
                        .param("preferred_language", "en")
                        .param("unit_system", "METRIC")
                        .with(csrf())
                        .with(user(adminUsername)))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/user-management :: users-list"))
                .andExpect(model().attributeExists("successMessage"))
                .andExpect(model().attributeExists("users"))
                .andExpect(model().attributeExists("currentUsername"))
                .andExpect(model().attribute("isAdmin", true));

        // Verify user was created in database
        Optional<User> userAfter = userJdbcService.findByUsername(newUsername);
        assertThat(userAfter).isPresent();
        assertThat(userAfter.get().getUsername()).isEqualTo(newUsername);
        assertThat(userAfter.get().getDisplayName()).isEqualTo(newDisplayName);
        assertThat(userAfter.get().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void createUser_AsRegularUser_ShouldShowAccessDenied() throws Exception {
        // Create the test user as a regular user
        String testUsername = randomUsername();
        userJdbcService.createUser(testUsername, "Test User", "password");
        
        String newUsername = randomUsername();
        String newDisplayName = "New User";
        String newPassword = "password123";

        mockMvc.perform(post("/settings/users")
                        .param("username", newUsername)
                        .param("displayName", newDisplayName)
                        .param("password", newPassword)
                        .param("role", "USER")
                        .param("preferred_language", "en")
                        .param("unit_system", "METRIC")
                        .with(csrf())
                        .with(user(testUsername)))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/user-management :: user-form-page"))
                .andExpect(model().attributeExists("errorMessage"));

        // Verify user was NOT created in database
        Optional<User> userAfter = userJdbcService.findByUsername(newUsername);
        assertThat(userAfter).isEmpty();
    }

    @Test
    void createUser_WithInvalidData_ShouldShowError() throws Exception {
        // Create the admin user
        String adminUsername = randomUsername();
        User adminUser = userJdbcService.createUser(adminUsername, "Admin User", "password");
        adminUser = adminUser.withRole(Role.ADMIN);
        userJdbcService.updateUser(adminUser);
        
        mockMvc.perform(post("/settings/users")
                        .param("username", "")
                        .param("displayName", "")
                        .param("password", "")
                        .param("role", "USER")
                        .param("preferred_language", "en")
                        .param("unit_system", "METRIC")
                        .with(csrf())
                        .with(user(adminUsername)))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/user-management :: users-list"))
                .andExpect(model().attributeExists("errorMessage"))
                .andExpect(model().attribute("isAdmin", true));
    }

    @Test
    void updateUser_AsAdmin_ShouldUpdateExistingUser() throws Exception {
        // Create the admin user
        String adminUsername = randomUsername();
        User adminUser = userJdbcService.createUser(adminUsername, "Admin User", "password");
        adminUser = adminUser.withRole(Role.ADMIN);
        userJdbcService.updateUser(adminUser);
        
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
                        .param("role", "ADMIN")
                        .param("preferred_language", "en")
                        .param("unit_system", "METRIC")
                        .with(csrf())
                        .with(user(adminUsername)))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/user-management :: users-list"))
                .andExpect(model().attributeExists("successMessage"))
                .andExpect(model().attributeExists("users"))
                .andExpect(model().attribute("isAdmin", true));

        // Verify user was updated in database
        User updatedUser = userJdbcService.findById(userId).orElseThrow();
        assertThat(updatedUser.getUsername()).isEqualTo(updatedUsername);
        assertThat(updatedUser.getDisplayName()).isEqualTo(updatedDisplayName);
        assertThat(updatedUser.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void deleteUser_AsAdmin_ShouldDeleteUser() throws Exception {
        // Create the admin user
        String adminUsername = randomUsername();
        User adminUser = userJdbcService.createUser(adminUsername, "Admin User", "password");
        adminUser = adminUser.withRole(Role.ADMIN);
        userJdbcService.updateUser(adminUser);
        
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
                        .with(csrf())
                        .with(user(adminUsername)))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/user-management :: users-list"))
                .andExpect(model().attributeExists("successMessage"))
                .andExpect(model().attributeExists("users"))
                .andExpect(model().attribute("isAdmin", true));

        // Verify user was deleted from database
        assertThat(userJdbcService.findById(userId)).isEmpty();
    }

    @Test
    void deleteUser_AsRegularUser_ShouldShowAccessDenied() throws Exception {
        // Create the test user as a regular user
        String testUsername = randomUsername();
        userJdbcService.createUser(testUsername, "Test User", "password");
        
        // Create another user to try to delete
        String usernameToDelete = randomUsername();
        String displayName = "Delete User";
        String password = "password123";
        userJdbcService.createUser(usernameToDelete, displayName, password);

        User createdUser = userJdbcService.findByUsername(usernameToDelete).orElseThrow();
        Long userId = createdUser.getId();

        mockMvc.perform(post("/settings/users/{userId}/delete", userId)
                        .with(csrf())
                        .with(user(testUsername)))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/user-management :: user-form-page"))
                .andExpect(model().attributeExists("errorMessage"));

        // Verify user was NOT deleted from database
        assertThat(userJdbcService.findById(userId)).isPresent();
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
                .andExpect(view().name("fragments/user-management :: user-form-page"))
                .andExpect(model().attributeExists("errorMessage"))
                .andExpect(model().attribute("isAdmin", false));

        // Verify user was NOT deleted from database
        assertThat(userJdbcService.findById(userId)).isPresent();
    }

    @Test
    void getUserForm_AsAdmin_WithoutUserId_ShouldReturnEmptyForm() throws Exception {
        // Create the admin user
        String adminUsername = randomUsername();
        User adminUser = userJdbcService.createUser(adminUsername, "Admin User", "password");
        adminUser = adminUser.withRole(Role.ADMIN);
        userJdbcService.updateUser(adminUser);
        
        mockMvc.perform(get("/settings/user-form")
                        .with(user(adminUsername)))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/user-management :: user-form-page"))
                .andExpect(model().attributeDoesNotExist("userId"))
                .andExpect(model().attributeDoesNotExist("username"))
                .andExpect(model().attributeDoesNotExist("displayName"))
                .andExpect(model().attribute("isAdmin", true));
    }

    @Test
    void getUserForm_AsRegularUser_WithoutUserId_ShouldShowAccessDenied() throws Exception {
        // Create the test user as a regular user
        String testUsername = randomUsername();
        userJdbcService.createUser(testUsername, "Test User", "password");
        
        mockMvc.perform(get("/settings/user-form")
                        .with(user(testUsername)))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/user-management :: user-form-page"))
                .andExpect(model().attributeExists("errorMessage"));
    }

    @Test
    void getUserForm_AsAdmin_WithUserId_ShouldReturnPopulatedForm() throws Exception {
        // Create the admin user
        String adminUsername = randomUsername();
        User adminUser = userJdbcService.createUser(adminUsername, "Admin User", "password");
        adminUser = adminUser.withRole(Role.ADMIN);
        userJdbcService.updateUser(adminUser);
        
        Long userId = 1L;
        String username = "formuser";
        String displayName = "Form User";
        String role = "USER";

        mockMvc.perform(get("/settings/user-form")
                        .param("userId", userId.toString())
                        .param("username", username)
                        .param("displayName", displayName)
                        .param("role", role)
                        .with(user(adminUsername)))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/user-management :: user-form-page"))
                .andExpect(model().attribute("userId", userId))
                .andExpect(model().attribute("username", username))
                .andExpect(model().attribute("displayName", displayName))
                .andExpect(model().attribute("selectedRole", role))
                .andExpect(model().attributeExists("unitSystems"))
                .andExpect(model().attributeExists("selectedUnitSystem"))
                .andExpect(model().attribute("isAdmin", true));
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
                        .param("role", "USER")
                        .param("preferred_language", "en")
                        .param("unit_system", "METRIC")
                        .with(csrf())
                        .with(user(currentUsername)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/user-management :: user-form-page"))
                .andExpect(model().attributeExists("successMessage"))
                .andExpect(model().attribute("requireRelogin", true))
                .andExpect(model().attribute("newUsername", newUsername));

        // Verify user was updated in database
        User updatedUser = userJdbcService.findById(userId).orElseThrow();
        assertThat(updatedUser.getUsername()).isEqualTo(newUsername);
    }

    @Test
    void getUserForm_AsAdmin_WithoutUserId_ShouldIncludeAvailableUsers() throws Exception {
        // Create the admin user
        String adminUsername = randomUsername();
        User adminUser = userJdbcService.createUser(adminUsername, "Admin User", "password");
        adminUser = adminUser.withRole(Role.ADMIN);
        userJdbcService.updateUser(adminUser);
        
        mockMvc.perform(get("/settings/user-form")
                        .with(user(adminUsername)))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/user-management :: user-form-page"))
                .andExpect(model().attributeExists("unitSystems"))
                .andExpect(model().attribute("selectedLanguage", "en"))
                .andExpect(model().attribute("selectedUnitSystem", "METRIC"))
                .andExpect(model().attribute("selectedRole", "USER"))
                .andExpect(model().attribute("isAdmin", true));
    }
}
