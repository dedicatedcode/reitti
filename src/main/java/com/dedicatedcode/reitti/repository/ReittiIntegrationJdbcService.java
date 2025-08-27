package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.dto.ReittiRemoteInfo;
import com.dedicatedcode.reitti.model.ReittiIntegration;
import com.dedicatedcode.reitti.model.RemoteUser;
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
import java.util.Objects;
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
        ReittiIntegration.Status.valueOf(rs.getString("status")),
        rs.getTimestamp("created_at").toLocalDateTime(),
        rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null,
        rs.getTimestamp("last_used") != null ? rs.getTimestamp("last_used").toLocalDateTime() : null,
        rs.getLong("version"),
        rs.getString("last_message"),
        rs.getString("color")
    );

    private static final RowMapper<RemoteUser> REMOTE_USER_ROW_MAPPER = (rs, _) -> new RemoteUser(
        rs.getLong("remote_id"),
        rs.getString("display_name"),
        rs.getString("user_name"),
        rs.getLong("remote_version")
    );

    public List<ReittiIntegration> findAllByUser(User user) {
        String sql = "SELECT id, url, token, color, enabled, created_at, updated_at, last_used, version, status, last_message " +
                    "FROM reitti_integrations WHERE user_id = ? ORDER BY id DESC";
        return jdbcTemplate.query(sql, ROW_MAPPER, user.getId());
    }

    private Optional<ReittiIntegration> findById(Long id) {
        String sql = "SELECT id, url, token, color, enabled, created_at, updated_at, last_used, version, status, last_message " +
                "FROM reitti_integrations WHERE id = ?";
        List<ReittiIntegration> results = jdbcTemplate.query(sql, ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public Optional<ReittiIntegration> findByIdAndUser(Long id, User user) {
        String sql = "SELECT id, url, token, color, enabled, created_at, updated_at, last_used, version, status, last_message " +
                    "FROM reitti_integrations WHERE id = ? AND user_id = ?";
        List<ReittiIntegration> results = jdbcTemplate.query(sql, ROW_MAPPER, id, user.getId());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public ReittiIntegration create(User user, ReittiIntegration integration) {
        String sql = "INSERT INTO reitti_integrations (user_id, url, token, color, enabled, created_at, status, version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
        
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
            ps.setString(7, integration.getStatus().name());
            ps.setLong(8, 1L);
            return ps;
        }, keyHolder);

        Long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return this.findByIdAndUser(id, user).orElseThrow();
    }

    public Optional<ReittiIntegration> update(ReittiIntegration integration) throws OptimisticLockException {
        String sql = "UPDATE reitti_integrations SET url = ?, token = ?, color = ?, enabled = ?, updated_at = ?, last_used = ?, last_message = ?, status = ?, version = version + 1 " +
                    "WHERE id = ? AND version = ? RETURNING id, url, token, color, enabled, created_at, updated_at, last_used, version, status, last_message";
        
        LocalDateTime now = LocalDateTime.now();
        List<ReittiIntegration> results = jdbcTemplate.query(sql, ROW_MAPPER, 
            integration.getUrl(), 
            integration.getToken(), 
            integration.getColor(), 
            integration.isEnabled(), 
            Timestamp.valueOf(now), 
            integration.getLastUsed().map(Timestamp::valueOf).orElse(null),
            integration.getLastMessage().orElse(null),
            integration.getStatus().name(),
            integration.getId(),
            integration.getVersion());
        
        if (results.isEmpty()) {
            Optional<ReittiIntegration> existing = findById(integration.getId());
            if (existing.isPresent()) {
                throw new OptimisticLockException("The integration has been modified by another process. Please refresh and try again.");
            }
            return Optional.empty();
        }
        
        return Optional.of(results.getFirst());
    }

    public boolean delete(ReittiIntegration integration) throws OptimisticLockException {
        String sql = "DELETE FROM reitti_integrations WHERE id = ? AND version = ?";
        int rowsAffected = jdbcTemplate.update(sql, integration.getId(), integration.getVersion());
        
        if (rowsAffected == 0) {
            Optional<ReittiIntegration> existing = findById(integration.getId());
            if (existing.isPresent()) {
                throw new OptimisticLockException("The integration has been modified by another process. Please refresh and try again.");
            }
            return false;
        }
        
        return true;
    }

    public Optional<RemoteUser> findByIntegration(ReittiIntegration reittiIntegration) {
        List<RemoteUser> results = this.jdbcTemplate.query("SELECT remote_id, remote_version, user_name, display_name FROM remote_user_info WHERE integration_id = ?", REMOTE_USER_ROW_MAPPER, reittiIntegration.getId());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public void store(ReittiIntegration integration, RemoteUser remoteUser, byte[] avatarData, String mimeType) {
        this.jdbcTemplate.update("DELETE FROM remote_user_info WHERE integration_id = ?", integration.getId());
        this.jdbcTemplate.update("INSERT INTO remote_user_info(integration_id, remote_id, remote_version, user_name, display_name, binary_data, mime_type) VALUES (?,?,?,?,?,?,?)",
                integration.getId(),
                remoteUser.getRemoteId(),
                remoteUser.getRemoteVersion(),
                remoteUser.getUserName(),
                remoteUser.getDisplayName(),
                avatarData,
                mimeType);
    }
}
