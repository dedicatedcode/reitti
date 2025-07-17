package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private PasswordEncoder passwordEncoder;

    @Test
    void testCreateAndFindUser() {
        User created = userJdbcService.createUser("testuser", "Test User", "password");
        assertNotNull(created.getId());
        assertEquals("testuser", created.getUsername());
        assertEquals("Test User", created.getDisplayName());
        assertTrue(passwordEncoder.matches("password", created.getPassword()));
        assertEquals("USER", created.getRole());
        assertEquals(1L, created.getVersion());

        Optional<User> foundOpt = userJdbcService.findById(created.getId());
        assertTrue(foundOpt.isPresent());
        User found = foundOpt.get();
        assertEquals(created.getId(), found.getId());
        assertEquals("testuser", found.getUsername());
        assertEquals("Test User", found.getDisplayName());
        assertEquals(created.getPassword(), found.getPassword());
        assertEquals("USER", found.getRole());
        assertEquals(1L, found.getVersion());

        Optional<User> foundByUsernameOpt = userJdbcService.findByUsername("testuser");
        assertTrue(foundByUsernameOpt.isPresent());
        assertEquals(created.getId(), foundByUsernameOpt.get().getId());
    }

    @Test
    void testUpdateUser() {
        User user = userJdbcService.createUser("updateuser", "Update User", "password");
        User updated = userJdbcService.updateUser(user.getId(), "updateduser", "Updated User", "newpassword");

        assertEquals(user.getId(), updated.getId());
        assertEquals("updateduser", updated.getUsername());
        assertEquals("Updated User", updated.getDisplayName());
        assertTrue(passwordEncoder.matches("newpassword", updated.getPassword()));
        assertEquals("USER", updated.getRole()); // Role is not updated
        assertEquals(2L, updated.getVersion());

        // Test optimistic locking
        assertThrows(OptimisticLockingFailureException.class, () -> {
            userJdbcService.updateUser(user.getId(), "anotherupdate", "Another Update", "password");
        });
    }

    @Test
    void testDeleteUser() {
        User user = userJdbcService.createUser("deleteuser", "Delete User", "password");
        assertNotNull(user.getId());
        assertTrue(userJdbcService.findById(user.getId()).isPresent());

        userJdbcService.deleteUser(user.getId());
        assertFalse(userJdbcService.findById(user.getId()).isPresent());
    }

    @Test
    void testGetAllUsers() {
        userJdbcService.createUser("user1", "User One", "pass");
        userJdbcService.createUser("user2", "User Two", "pass");

        List<User> users = userJdbcService.getAllUsers();
        assertTrue(users.stream().anyMatch(u -> u.getUsername().equals("user1")));
        assertTrue(users.stream().anyMatch(u -> u.getUsername().equals("user2")));
    }
}
