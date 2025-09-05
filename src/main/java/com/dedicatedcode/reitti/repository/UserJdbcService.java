package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class UserJdbcService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public UserJdbcService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return findAll();
    }

    @CacheEvict(value = "users", allEntries = true)
    public void deleteUser(Long userId) {
        this.jdbcTemplate.update("DELETE FROM user_avatars WHERE user_id = ?", userId);
        this.jdbcTemplate.update("DELETE FROM user_settings WHERE user_id = ?", userId);
        String sql = "DELETE FROM users WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, userId);
        if (rowsAffected == 0) {
            throw new EmptyResultDataAccessException("No user found with id: " + userId, 1);
        }
    }

    @CacheEvict(value = "users", allEntries = true)
    public User createUser(String username, String displayName, String password) {
        String encodedPassword = passwordEncoder.encode(password);
        Role role = Role.USER;
        String sql = "INSERT INTO users (username, password, display_name, role, version) VALUES (?, ?, ?, ?, 1) RETURNING id";
        Long id = jdbcTemplate.queryForObject(sql, Long.class, username, encodedPassword, displayName, role.name());
        return new User(id, username, encodedPassword, displayName, role, 1L);
    }

    @CacheEvict(value = "users", allEntries = true)
    public User updateUser(User userToUpdate) {
        String sql = "UPDATE users SET username = ?, password = ?, display_name = ?, role = ?, version = version + 1 WHERE id = ? AND version = ? RETURNING version";

        try {
            Long newVersion = jdbcTemplate.queryForObject(sql, Long.class,
                userToUpdate.getUsername(), 
                userToUpdate.getPassword(), 
                userToUpdate.getDisplayName(), 
                userToUpdate.getRole().name(),
                userToUpdate.getId(), 
                userToUpdate.getVersion());

            return userToUpdate.withVersion(newVersion);
        } catch (EmptyResultDataAccessException e) {
            throw new OptimisticLockingFailureException("User was modified by another transaction");
        }
    }

    // Repository-like methods using JdbcTemplate
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "users")
    public Optional<User> findById(Long id) {
        String sql = "SELECT id, username, password, display_name, role, version FROM users WHERE id = ?";
        try {
            User user = jdbcTemplate.queryForObject(sql, this::mapRowToUser, id);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    @Transactional(readOnly = true)
    @Cacheable("users")
    public Optional<User> findByUsername(String username) {
        String sql = "SELECT id, username, password, display_name, role, version FROM users WHERE username = ?";
        try {
            User user = jdbcTemplate.queryForObject(sql, this::mapRowToUser, username);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    @Transactional(readOnly = true)
    public List<User> findAll() {
        String sql = "SELECT id, username, password, display_name, role, version FROM users ORDER BY username";
        return jdbcTemplate.query(sql, this::mapRowToUser);
    }

    private User mapRowToUser(ResultSet rs, int rowNum) throws SQLException {
        return new User(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("password"),
            rs.getString("display_name"),
            Role.valueOf(rs.getString("role")),
            rs.getLong("version")
        );
    }
}
