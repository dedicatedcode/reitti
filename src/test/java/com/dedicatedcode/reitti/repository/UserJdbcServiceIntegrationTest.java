package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.security.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
@Transactional
class UserJdbcServiceIntegrationTest {

    @Autowired
    private UserJdbcService userJdbcService;

    @Autowired
    private CacheManager cacheManager;

    @Test
    void testCreateAndFindUser() {
        User created = userJdbcService.createUser(new User("testuser", "Test User").withPassword("password"));
        assertNotNull(created.getId());
        assertEquals("testuser", created.getUsername());
        assertEquals("Test User", created.getDisplayName());
        assertEquals("password", created.getPassword());
        assertEquals(Role.USER, created.getRole());
        assertEquals(1L, created.getVersion());

        Optional<User> foundOpt = userJdbcService.findById(created.getId());
        assertTrue(foundOpt.isPresent());
        User found = foundOpt.get();
        assertEquals(created.getId(), found.getId());
        assertEquals("testuser", found.getUsername());
        assertEquals("Test User", found.getDisplayName());
        assertEquals(created.getPassword(), found.getPassword());
        assertEquals(Role.USER, found.getRole());
        assertEquals(1L, found.getVersion());

        Optional<User> foundByUsernameOpt = userJdbcService.findByUsername("testuser");
        assertTrue(foundByUsernameOpt.isPresent());
        assertEquals(created.getId(), foundByUsernameOpt.get().getId());
    }

    @Test
    void testUpdateUser() {
        User user = userJdbcService.createUser(new User("updateuser", "Update User").withPassword("password"));
        User userToUpdate = new User(user.getId(), "updateduser", "new password", "Updated User", null, "oidc:1344", Role.ADMIN, user.getVersion());
        User updated = userJdbcService.updateUser(userToUpdate);

        assertEquals(user.getId(), updated.getId());
        assertEquals("updateduser", updated.getUsername());
        assertEquals("Updated User", updated.getDisplayName());
        assertEquals("new password", updated.getPassword());
        assertEquals("oidc:1344", updated.getExternalId());
        assertEquals(Role.ADMIN, updated.getRole());
        assertEquals(2L, updated.getVersion());
    }

    @Test
    void testDeleteUser() {
        User user = userJdbcService.createUser(new User("deleteuser", "Delete User").withPassword("password"));
        assertNotNull(user.getId());
        assertTrue(userJdbcService.findById(user.getId()).isPresent());

        userJdbcService.deleteUser(user.getId());
        assertFalse(userJdbcService.findById(user.getId()).isPresent());
    }

    @Test
    void testGetAllUsers() {
        userJdbcService.createUser(new User("user1", "User One").withPassword("pass"));
        userJdbcService.createUser(new User("user2", "User Two").withPassword("pass"));

        List<User> users = userJdbcService.getAllUsers();
        assertTrue(users.stream().anyMatch(u -> u.getUsername().equals("user1")));
        assertTrue(users.stream().anyMatch(u -> u.getUsername().equals("user2")));
    }

    @Test
    void testCacheEvictionOnUpdate() {
        User user = userJdbcService.createUser(new User("cacheuser", "Cache User").withPassword("password"));
        String oldUsername = user.getUsername();
        Long userId = user.getId();

        // populate cache
        userJdbcService.findByUsername(oldUsername);
        userJdbcService.findById(userId);

        // check cache is populated
        assertNotNull(cacheManager.getCache("users").get(oldUsername, User.class));
        assertNotNull(cacheManager.getCache("users").get(userId, User.class));

        // update user
        String newUsername = "newcacheuser";
        String newEncodedPassword = "newpassword";
        User userToUpdate = new User(userId, newUsername, newEncodedPassword, "New Cache User", null, null, Role.USER, user.getVersion());
        userJdbcService.updateUser(userToUpdate);

        // check cache is evicted
        assertNull(cacheManager.getCache("users").get(oldUsername));
        assertNull(cacheManager.getCache("users").get(userId));

        // check that fetching new user works and populates cache
        Optional<User> byId = userJdbcService.findById(userId);
        assertTrue(byId.isPresent());
        assertEquals(newUsername, byId.get().getUsername());
        assertNotNull(cacheManager.getCache("users").get(userId, User.class));

        // check that fetching by old username does not work
        assertFalse(userJdbcService.findByUsername(oldUsername).isPresent());

        // check that fetching by new username works and populates cache
        Optional<User> byNewUsername = userJdbcService.findByUsername(newUsername);
        assertTrue(byNewUsername.isPresent());
        assertEquals(newUsername, byNewUsername.get().getUsername());
        assertNotNull(cacheManager.getCache("users").get(newUsername, User.class));
    }
}
