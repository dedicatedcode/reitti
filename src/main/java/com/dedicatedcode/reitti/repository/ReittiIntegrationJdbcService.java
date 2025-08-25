package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.ReittiIntegration;
import com.dedicatedcode.reitti.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ReittiIntegrationJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public ReittiIntegrationJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<ReittiIntegration> ROW_MAPPER = new RowMapper<ReittiIntegration>() {
        @Override
        public ReittiIntegration mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ReittiIntegration(
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
        }
    };

    public List<ReittiIntegration> findAllByUser(User user) {
        String sql = "SELECT id, url, token, color, enabled, created_at, updated_at, last_used, version, last_message " +
                    "FROM reitti_integrations WHERE user_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, ROW_MAPPER, user.getId());
    }

    public Optional<ReittiIntegration> findByIdAndUser(Long id, User user) {
        String sql = "SELECT id, url, token, color, enabled, created_at, updated_at, last_used, version, last_message " +
                    "FROM reitti_integrations WHERE id = ? AND user_id = ?";
        List<ReittiIntegration> results = jdbcTemplate.query(sql, ROW_MAPPER, id, user.getId());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public ReittiIntegration create(User user, String url, String token, String color, boolean enabled) {
        String sql = "INSERT INTO reitti_integrations (user_id, url, token, color, enabled, created_at, version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id";
        
        KeyHolder keyHolder = new GeneratedKeyHolder();
        LocalDateTime now = LocalDateTime.now();
        
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setLong(1, user.getId());
            ps.setString(2, url);
            ps.setString(3, token);
            ps.setString(4, color);
            ps.setBoolean(5, enabled);
            ps.setTimestamp(6, Timestamp.valueOf(now));
            ps.setLong(7, 1L);
            return ps;
        }, keyHolder);

        Long id = keyHolder.getKey().longValue();
        return new ReittiIntegration(
            id,
            url,
            token,
            enabled,
            enabled ? ReittiIntegration.Status.ENABLED : ReittiIntegration.Status.DISABLED,
            now,
            null,
            null,
            1L,
            null,
            color
        );
    }

    public Optional<ReittiIntegration> update(Long id, User user, String url, String token, String color, boolean enabled) {
        String sql = "UPDATE reitti_integrations SET url = ?, token = ?, color = ?, enabled = ?, updated_at = ?, version = version + 1 " +
                    "WHERE id = ? AND user_id = ? RETURNING id, url, token, color, enabled, created_at, updated_at, last_used, version, last_message";
        
        LocalDateTime now = LocalDateTime.now();
        List<ReittiIntegration> results = jdbcTemplate.query(sql, ROW_MAPPER, url, token, color, enabled, Timestamp.valueOf(now), id, user.getId());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public boolean toggleEnabled(Long id, User user) {
        String sql = "UPDATE reitti_integrations SET enabled = NOT enabled, updated_at = ?, version = version + 1 " +
                    "WHERE id = ? AND user_id = ?";
        LocalDateTime now = LocalDateTime.now();
        int rowsAffected = jdbcTemplate.update(sql, Timestamp.valueOf(now), id, user.getId());
        return rowsAffected > 0;
    }

    public boolean updateLastUsed(Long id, User user, LocalDateTime lastUsed, String lastMessage) {
        String sql = "UPDATE reitti_integrations SET last_used = ?, last_message = ?, version = version + 1 " +
                    "WHERE id = ? AND user_id = ?";
        int rowsAffected = jdbcTemplate.update(sql, Timestamp.valueOf(lastUsed), lastMessage, id, user.getId());
        return rowsAffected > 0;
    }

    public boolean delete(Long id, User user) {
        String sql = "DELETE FROM reitti_integrations WHERE id = ? AND user_id = ?";
        int rowsAffected = jdbcTemplate.update(sql, id, user.getId());
        return rowsAffected > 0;
    }
}
