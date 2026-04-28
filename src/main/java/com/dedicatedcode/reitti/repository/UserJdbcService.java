package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.Temporal;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class UserJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public UserJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return findAll();
    }

    @CacheEvict(value = "users", allEntries = true)
    public void deleteUser(Long userId) {
        this.jdbcTemplate.update("DELETE FROM user_avatars WHERE user_id = ?", userId);
        this.jdbcTemplate.update("DELETE FROM user_settings WHERE user_id = ?", userId);
        this.jdbcTemplate.update("DELETE FROM visit_detection_parameters WHERE user_id = ?", userId);
        String sql = "DELETE FROM users WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, userId);
        if (rowsAffected == 0) {
            throw new EmptyResultDataAccessException("No user found with id: " + userId, 1);
        }
    }

    @CacheEvict(value = "users", allEntries = true)
    public User createUser(User user) {
        String sql = "INSERT INTO users (username, password, display_name, role, profile_url, external_id, version) VALUES (?, ?, ?, ?, ?, ?, 1) RETURNING id";
        Long id = jdbcTemplate.queryForObject(sql, Long.class,
                user.getUsername(),
                user.getPassword(),
                user.getDisplayName(),
                user.getRole().name(),
                user.getProfileUrl(),
                user.getExternalId());
        return this.findById(id).orElseThrow(() -> new RuntimeException("User not found with id: " + id));
    }

    @CacheEvict(value = "users", allEntries = true)
    public User updateUser(User userToUpdate) {
        String sql = "UPDATE users SET username = ?, password = ?, display_name = ?, role = ?, profile_url = ?, external_id = ?, version = version + 1 WHERE id = ? AND version = ? RETURNING version";

        try {
            Long newVersion = jdbcTemplate.queryForObject(sql, Long.class,
                userToUpdate.getUsername(), 
                userToUpdate.getPassword(), 
                userToUpdate.getDisplayName(), 
                userToUpdate.getRole().name(),
                userToUpdate.getProfileUrl(),
                userToUpdate.getExternalId(),
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
        String sql = "SELECT id, username, password, display_name, role, profile_url, external_id, version FROM users WHERE id = ?";
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
        String sql = "SELECT id, username, password, display_name, role, profile_url, external_id, version FROM users WHERE username = ?";
        try {
            User user = jdbcTemplate.queryForObject(sql, this::mapRowToUser, username);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    @Transactional(readOnly = true)
    @Cacheable("users")
    public Optional<User> findByExternalId(String externalId) {
        String sql = "SELECT id, username, password, display_name, role, profile_url, external_id, version FROM users WHERE external_id = ?";
        try {
            User user = jdbcTemplate.queryForObject(sql, this::mapRowToUser, externalId);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    @Transactional(readOnly = true)
    public List<User> findAll() {
        String sql = "SELECT id, username, password, display_name, role, profile_url, external_id, version FROM users ORDER BY username";
        return jdbcTemplate.query(sql, this::mapRowToUser);
    }

    private User mapRowToUser(ResultSet rs, int rowNum) throws SQLException {
        return new User(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("password"),
            rs.getString("display_name"), rs.getString("profile_url"), rs.getString("external_id"),
            Role.valueOf(rs.getString("role")),
            rs.getLong("version")
        );
    }

    public void setLastDataModificationAt(User user, Instant lastDataModificationAt) {
        this.jdbcTemplate.update("UPDATE users SET last_data_modified_at = ? WHERE id = ?", Timestamp.from(lastDataModificationAt), user.getId());
    }

    public Optional<Instant> getLastDataModificationAt(User user) {
        return this.jdbcTemplate.queryForObject("SELECT last_data_modified_at FROM users WHERE id = ?", (rs, rowNum) -> {
            Timestamp lastDataModifiedAt = rs.getTimestamp("last_data_modified_at");
            if (lastDataModifiedAt == null) {
                return Optional.empty();
            } else {
                return Optional.of(lastDataModifiedAt.toInstant());
            }
        }, user.getId());
    }
}
