package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.model.security.UserSharing;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserSharingJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public UserSharingJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public Set<Long> getSharedUserIds(Long sharingUserId) {
        String sql = "SELECT shared_with_user_id FROM user_sharing WHERE sharing_user_id = ?";
        return new HashSet<>(jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("shared_with_user_id"), sharingUserId));
    }

    @Transactional(readOnly = true)
    public List<UserSharing> findBySharingUser(Long sharingUserId) {
        String sql = "SELECT id, sharing_user_id, shared_with_user_id, created_at, color, version FROM user_sharing WHERE sharing_user_id = ?";
        return jdbcTemplate.query(sql, this::mapRowToUserSharing, sharingUserId);
    }

    @Transactional(readOnly = true)
    public List<UserSharing> findBySharedWithUser(Long sharedWithUserId) {
        String sql = "SELECT id, sharing_user_id, shared_with_user_id, created_at, color, version FROM user_sharing WHERE shared_with_user_id = ?";
        return jdbcTemplate.query(sql, this::mapRowToUserSharing, sharedWithUserId);
    }

    private void createSharing(Long sharingUserId, Long sharedWithUserId, String color) {
        String sql = "INSERT INTO user_sharing (sharing_user_id, shared_with_user_id, created_at, color, version) VALUES (?, ?, now(), ?, 1)";
        jdbcTemplate.update(sql, sharingUserId, sharedWithUserId, color);
    }

    public void create(User user, Set<UserSharing> toCreate) {
        for (UserSharing userSharing : toCreate) {
            createSharing(user.getId(), userSharing.getSharedWithUserId(), userSharing.getColor());
        }
    }
    private UserSharing mapRowToUserSharing(ResultSet rs, int rowNum) throws SQLException {
        return new UserSharing(
                rs.getLong("id"),
                rs.getLong("sharing_user_id"),
                rs.getLong("shared_with_user_id"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("color"),
                rs.getLong("version")
        );
    }

    public void delete(Set<UserSharing> toDelete) {
        this.jdbcTemplate.batchUpdate("DELETE FROM user_sharing WHERE id = ?", toDelete.stream().map(userSharing -> new Object[]{userSharing.getId()}).collect(Collectors.toList()));
    }

    public void dismissSharedAccess(Long sharingId, Long sharedWithUserId) {
        // Verify that the sharing belongs to the user before deleting
        String sql = "DELETE FROM user_sharing WHERE id = ? AND shared_with_user_id = ?";
        int rowsAffected = jdbcTemplate.update(sql, sharingId, sharedWithUserId);
        if (rowsAffected == 0) {
            throw new IllegalArgumentException("Sharing not found or access denied");
        }
    }

    public void updateSharingColor(Long sharingId, Long sharedWithUserId, String color) {
        // Verify that the sharing belongs to the user before updating
        String sql = "UPDATE user_sharing SET color = ?, version = version + 1 WHERE id = ? AND shared_with_user_id = ?";
        int rowsAffected = jdbcTemplate.update(sql, color, sharingId, sharedWithUserId);
        if (rowsAffected == 0) {
            throw new IllegalArgumentException("Sharing not found or access denied");
        }
    }

}
