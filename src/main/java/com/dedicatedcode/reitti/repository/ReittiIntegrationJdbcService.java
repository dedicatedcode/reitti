package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.ReittiIntegration;
import com.dedicatedcode.reitti.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ReittiIntegrationJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public ReittiIntegrationJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<ReittiIntegration> ROW_MAPPER = (rs, _) -> new ReittiIntegration(
        rs.getLong("id"),
        rs.getString("url"),
        rs.getString("token"),
        rs.getBoolean("enabled"),
        ReittiIntegration.Status.valueOf(rs.getBoolean("enabled") ? "ENABLED" : "DISABLED"),
        rs.getTimestamp("created_at").toLocalDateTime(),
        rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null,
        rs.getTimestamp("last_used") != null ? rs.getTimestamp("last_used").toLocalDateTime() : null,
        rs.getLong("version"),
        rs.getString("last_message"),
        rs.getString("color")
    );

    public List<ReittiIntegration> findAllByUser(User user) {
        String sql = "SELECT id, url, token, color, enabled, created_at, updated_at, last_used, version, last_message " +
                    "FROM reitti_integrations WHERE user_id = ? ORDER BY id DESC";
        return jdbcTemplate.query(sql, ROW_MAPPER, user.getId());
    }

    public Optional<ReittiIntegration> findByIdAndUser(Long id, User user) {
        String sql = "SELECT id, url, token, color, enabled, created_at, updated_at, last_used, version, last_message " +
                    "FROM reitti_integrations WHERE id = ? AND user_id = ?";
        List<ReittiIntegration> results = jdbcTemplate.query(sql, ROW_MAPPER, id, user.getId());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public ReittiIntegration create(User user, ReittiIntegration integration) {
        String sql = "INSERT INTO reitti_integrations (user_id, url, token, color, enabled, created_at, version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id";
        
        KeyHolder keyHolder = new GeneratedKeyHolder();
        LocalDateTime now = LocalDateTime.now();
        
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setLong(1, user.getId());
            ps.setString(2, integration.getUrl());
            ps.setString(3, integration.getToken());
            ps.setString(4, integration.getColor());
            ps.setBoolean(5, integration.isEnabled());
            ps.setTimestamp(6, Timestamp.valueOf(now));
            ps.setLong(7, 1L);
            return ps;
        }, keyHolder);

        Long id = keyHolder.getKey().longValue();
        return this.findByIdAndUser(id, user).orElseThrow();
    }

    public Optional<ReittiIntegration> update(User user, ReittiIntegration integration) throws OptimisticLockException {
        String sql = "UPDATE reitti_integrations SET url = ?, token = ?, color = ?, enabled = ?, updated_at = ?, version = version + 1 " +
                    "WHERE id = ? AND user_id = ? AND version = ? RETURNING id, url, token, color, enabled, created_at, updated_at, last_used, version, last_message";
        
        LocalDateTime now = LocalDateTime.now();
        List<ReittiIntegration> results = jdbcTemplate.query(sql, ROW_MAPPER, 
            integration.getUrl(), 
            integration.getToken(), 
            integration.getColor(), 
            integration.isEnabled(), 
            Timestamp.valueOf(now), 
            integration.getId(), 
            user.getId(), 
            integration.getVersion());
        
        if (results.isEmpty()) {
            Optional<ReittiIntegration> existing = findByIdAndUser(integration.getId(), user);
            if (existing.isPresent()) {
                throw new OptimisticLockException("The integration has been modified by another process. Please refresh and try again.");
            }
            return Optional.empty();
        }
        
        return Optional.of(results.get(0));
    }

    public boolean updateLastUsed(User user, ReittiIntegration integration, LocalDateTime lastUsed, String lastMessage) throws OptimisticLockException {
        String sql = "UPDATE reitti_integrations SET last_used = ?, last_message = ?, version = version + 1 " +
                    "WHERE id = ? AND user_id = ? AND version = ?";
        int rowsAffected = jdbcTemplate.update(sql, Timestamp.valueOf(lastUsed), lastMessage, integration.getId(), user.getId(), integration.getVersion());
        
        if (rowsAffected == 0) {
            // Check if the record exists but version doesn't match
            Optional<ReittiIntegration> existing = findByIdAndUser(integration.getId(), user);
            if (existing.isPresent()) {
                throw new OptimisticLockException("The integration has been modified by another process. Please refresh and try again.");
            }
            return false;
        }
        
        return true;
    }

    public boolean delete(User user, ReittiIntegration integration) throws OptimisticLockException {
        String sql = "DELETE FROM reitti_integrations WHERE id = ? AND user_id = ? AND version = ?";
        int rowsAffected = jdbcTemplate.update(sql, integration.getId(), user.getId(), integration.getVersion());
        
        if (rowsAffected == 0) {
            // Check if the record exists but version doesn't match
            Optional<ReittiIntegration> existing = findByIdAndUser(integration.getId(), user);
            if (existing.isPresent()) {
                throw new OptimisticLockException("The integration has been modified by another process. Please refresh and try again.");
            }
            return false;
        }
        
        return true;
    }
}
