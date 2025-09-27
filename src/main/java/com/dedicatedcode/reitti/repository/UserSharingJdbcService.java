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
        String sql = "SELECT id, sharing_user_id, shared_with_user_id, created_at, version FROM user_sharing WHERE sharing_user_id = ?";
        return jdbcTemplate.query(sql, this::mapRowToUserSharing, sharingUserId);
    }

    @Transactional(readOnly = true)
    public List<UserSharing> findBySharedWithUser(Long sharedWithUserId) {
        String sql = "SELECT id, sharing_user_id, shared_with_user_id, created_at, version FROM user_sharing WHERE shared_with_user_id = ?";
        return jdbcTemplate.query(sql, this::mapRowToUserSharing, sharedWithUserId);
    }

    public void updateSharedUsers(Long sharingUserId, Set<Long> sharedUserIds) {
        Set<Long> currentSharedUsers = getSharedUserIds(sharingUserId);
        
        Set<Long> usersToAdd = sharedUserIds.stream()
                .filter(id -> !currentSharedUsers.contains(id))
                .collect(Collectors.toSet());
        
        Set<Long> usersToRemove = currentSharedUsers.stream()
                .filter(id -> !sharedUserIds.contains(id))
                .collect(Collectors.toSet());
        
        for (Long userId : usersToAdd) {
            createSharing(sharingUserId, userId);
        }
        
        for (Long userId : usersToRemove) {
            removeSharing(sharingUserId, userId);
        }
    }

    private void createSharing(Long sharingUserId, Long sharedWithUserId) {
        String sql = "INSERT INTO user_sharing (sharing_user_id, shared_with_user_id, created_at, version) VALUES (?, ?, now(), 1)";
        jdbcTemplate.update(sql, sharingUserId, sharedWithUserId);
    }

    private void removeSharing(Long sharingUserId, Long sharedWithUserId) {
        String sql = "DELETE FROM user_sharing WHERE sharing_user_id = ? AND shared_with_user_id = ?";
        jdbcTemplate.update(sql, sharingUserId, sharedWithUserId);
    }

    public void deleteAllSharingForUser(Long userId) {
        String sql1 = "DELETE FROM user_sharing WHERE sharing_user_id = ?";
        String sql2 = "DELETE FROM user_sharing WHERE shared_with_user_id = ?";
        jdbcTemplate.update(sql1, userId);
        jdbcTemplate.update(sql2, userId);
    }

    private UserSharing mapRowToUserSharing(ResultSet rs, int rowNum) throws SQLException {
        return new UserSharing(
                rs.getLong("id"),
                rs.getLong("sharing_user_id"),
                rs.getLong("shared_with_user_id"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getLong("version")
        );
    }
}
