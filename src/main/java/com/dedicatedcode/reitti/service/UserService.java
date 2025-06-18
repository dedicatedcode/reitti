package com.dedicatedcode.reitti.service;

import com.dedicatedcode.reitti.model.User;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class UserService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public UserService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public User getUserByUsername(String username) {
        return findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        return findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return findAll();
    }

    public void deleteUser(Long userId) {
        deleteById(userId);
    }
    
    public User createUser(String username, String displayName, String password) {
        User user = new User(null, username, passwordEncoder.encode(password), displayName, null);
        return save(user);
    }
    
    public User updateUser(Long userId, String username, String displayName, String password) {
        User user = findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
        
        String encodedPassword = user.getPassword();
        // Only update password if provided
        if (password != null && !password.trim().isEmpty()) {
            encodedPassword = passwordEncoder.encode(password);
        }
        
        User updatedUser = new User(user.getId(), username, encodedPassword, displayName, user.getVersion());
        return save(updatedUser);
    }

    // Repository-like methods using JdbcTemplate
    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        String sql = "SELECT id, username, password, display_name, version FROM users WHERE id = ?";
        try {
            User user = jdbcTemplate.queryForObject(sql, this::mapRowToUser, id);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        String sql = "SELECT id, username, password, display_name, version FROM users WHERE username = ?";
        try {
            User user = jdbcTemplate.queryForObject(sql, this::mapRowToUser, username);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    @Transactional(readOnly = true)
    public List<User> findAll() {
        String sql = "SELECT id, username, password, display_name, version FROM users ORDER BY username";
        return jdbcTemplate.query(sql, this::mapRowToUser);
    }
    
    public User save(User user) {
        if (user.getId() == null) {
            return insert(user);
        } else {
            return update(user);
        }
    }
    
    private User insert(User user) {
        String sql = "INSERT INTO users (username, password, display_name) VALUES (?, ?, ?) RETURNING id, version";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getDisplayName());
            return ps;
        }, keyHolder);
        
        Long id = keyHolder.getKey().longValue();
        Long version = 1L; // Initial version
        
        return new User(id, user.getUsername(), user.getPassword(), user.getDisplayName(), version);
    }
    
    private User update(User user) {
        String sql = "UPDATE users SET username = ?, password = ?, display_name = ?, version = version + 1 WHERE id = ? AND version = ? RETURNING version";
        
        try {
            Long newVersion = jdbcTemplate.queryForObject(sql, Long.class, 
                user.getUsername(), user.getPassword(), user.getDisplayName(), user.getId(), user.getVersion());
            
            return user.withVersion(newVersion);
        } catch (EmptyResultDataAccessException e) {
            throw new OptimisticLockingFailureException("User was modified by another transaction");
        }
    }
    
    public void deleteById(Long id) {
        String sql = "DELETE FROM users WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, id);
        if (rowsAffected == 0) {
            throw new EmptyResultDataAccessException("No user found with id: " + id, 1);
        }
    }
    
    private User mapRowToUser(ResultSet rs, int rowNum) throws SQLException {
        return new User(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("password"),
            rs.getString("display_name"),
            rs.getLong("version")
        );
    }
}
