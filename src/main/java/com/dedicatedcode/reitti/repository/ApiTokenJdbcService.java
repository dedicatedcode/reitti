package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.ApiToken;
import com.dedicatedcode.reitti.model.ApiTokenUsage;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.User;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ApiTokenJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public ApiTokenJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public Optional<ApiToken> findByToken(String token) {
        String sql = """
            SELECT at.id, at.token, at.name, at.created_at, at.last_used_at,
                   u.id as user_id, u.username, u.password, u.display_name, u.role, u.version as user_version
            FROM api_tokens at
            JOIN users u ON at.user_id = u.id
            WHERE at.token = ?
            """;
        try {
            ApiToken apiToken = jdbcTemplate.queryForObject(sql, this::mapRowToApiToken, token);
            return Optional.ofNullable(apiToken);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public List<ApiToken> findByUser(User user) {
        String sql = """
            SELECT at.id, at.token, at.name, at.created_at, at.last_used_at,
                   u.id as user_id, u.username, u.password, u.display_name, u.role, u.version as user_version
            FROM api_tokens at
            JOIN users u ON at.user_id = u.id
            WHERE at.user_id = ?
            ORDER BY at.created_at DESC
            """;
        return jdbcTemplate.query(sql, this::mapRowToApiToken, user.getId());
    }

    @Transactional(readOnly = true)
    public Optional<ApiToken> findById(Long id) {
        String sql = """
            SELECT at.id, at.token, at.name, at.created_at, at.last_used_at,
                   u.id as user_id, u.username, u.password, u.display_name, u.role, u.version as user_version
            FROM api_tokens at
            JOIN users u ON at.user_id = u.id
            WHERE at.id = ?
            """;
        try {
            ApiToken apiToken = jdbcTemplate.queryForObject(sql, this::mapRowToApiToken, id);
            return Optional.ofNullable(apiToken);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public ApiToken save(ApiToken apiToken) {
        if (apiToken.getId() == null) {
            return insert(apiToken);
        } else {
            return update(apiToken);
        }
    }

    private ApiToken insert(ApiToken apiToken) {
        String sql = "INSERT INTO api_tokens (token, user_id, name, created_at, last_used_at) VALUES (?, ?, ?, ?, ?) RETURNING id";
        
        Long id = jdbcTemplate.queryForObject(sql, Long.class,
            apiToken.getToken(),
            apiToken.getUser().getId(),
            apiToken.getName(),
            Timestamp.from(apiToken.getCreatedAt()),
            apiToken.getLastUsedAt() != null ? Timestamp.from(apiToken.getLastUsedAt()) : null
        );
        
        return new ApiToken(id, apiToken.getToken(), apiToken.getUser(), apiToken.getName(), 
                           apiToken.getCreatedAt(), apiToken.getLastUsedAt());
    }

    private ApiToken update(ApiToken apiToken) {
        String sql = "UPDATE api_tokens SET token = ?, name = ?, last_used_at = ? WHERE id = ?";
        
        int rowsAffected = jdbcTemplate.update(sql,
            apiToken.getToken(),
            apiToken.getName(),
            apiToken.getLastUsedAt() != null ? Timestamp.from(apiToken.getLastUsedAt()) : null,
            apiToken.getId()
        );
        
        if (rowsAffected == 0) {
            throw new EmptyResultDataAccessException("No ApiToken found with id: " + apiToken.getId(), 1);
        }
        
        return apiToken;
    }

    public void deleteById(Long id) {
        String sql = "DELETE FROM api_tokens WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, id);
        if (rowsAffected == 0) {
            throw new EmptyResultDataAccessException("No ApiToken found with id: " + id, 1);
        }
    }

    public void delete(ApiToken apiToken) {
        deleteById(apiToken.getId());
    }

    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        String sql = "SELECT COUNT(*) FROM api_tokens WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }

    @Transactional(readOnly = true)
    public long count() {
        String sql = "SELECT COUNT(*) FROM api_tokens";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0L;
    }

    private ApiToken mapRowToApiToken(ResultSet rs, int rowNum) throws SQLException {
        User user = new User(
            rs.getLong("user_id"),
            rs.getString("username"),
            rs.getString("password"),
            rs.getString("display_name"),
            Role.valueOf(rs.getString("role")),
            rs.getLong("user_version")
        );

        return new ApiToken(
            rs.getLong("id"),
            rs.getString("token"),
            user,
            rs.getString("name"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("last_used_at") != null ? rs.getTimestamp("last_used_at").toInstant() : null
        );
    }

    private ApiTokenUsage mapRowToApiUsage(ResultSet rs, int rowNum) throws SQLException {
        return new ApiTokenUsage(rs.getString("token"),
                rs.getString("name"),
                rs.getTimestamp("at").toLocalDateTime(),
                rs.getString("endpoint"),
                rs.getString("ip"));
    }

    public List<ApiTokenUsage> getUsages(User user, int maxRows) {
        return this.jdbcTemplate.query("SELECT t.token, t.name, au.at, au.endpoint, au.ip FROM api_tokens t RIGHT JOIN api_token_usages au on t.id = au.token_id WHERE t.user_id = ? ORDER BY au.at DESC LIMIT ?", this::mapRowToApiUsage, user.getId(), maxRows);
    }

    public void trackUsage(String token, String requestPath, String remoteIp) {
        this.jdbcTemplate.update("INSERT INTO api_token_usages(token_id, at, endpoint, ip) SELECT id, now(), ?, ? FROM api_tokens WHERE token = ?",
                requestPath, remoteIp, token);
    }
}
