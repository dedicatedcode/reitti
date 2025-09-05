package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.security.MagicLinkAccessLevel;
import com.dedicatedcode.reitti.model.security.MagicLinkToken;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class MagicLinkJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public MagicLinkJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public MagicLinkToken create(User user, String tokenHash, MagicLinkAccessLevel accessLevel, Instant expiryDate) {
        String sql = """
            INSERT INTO magic_link_tokens (user_id, token_hash, access_level, expiry_date, created_at)
            VALUES (?, ?, ?, ?, ?)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        Instant now = Instant.now();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setLong(1, user.getId());
            ps.setString(2, tokenHash);
            ps.setString(3, accessLevel.name());
            ps.setTimestamp(4, Timestamp.from(expiryDate));
            ps.setTimestamp(5, Timestamp.from(now));
            return ps;
        }, keyHolder);

        long id = keyHolder.getKey().longValue();
        return new MagicLinkToken(id, tokenHash, accessLevel, expiryDate, now, null, false);
    }

    public Optional<MagicLinkToken> update(long id, MagicLinkToken updatedToken) {
        String sql = """
            UPDATE magic_link_tokens 
            SET token_hash = ?, access_level = ?, expiry_date = ?, last_used_at = ?
            WHERE id = ?
            """;

        int rowsAffected = jdbcTemplate.update(sql,
                updatedToken.getTokenHash(),
                updatedToken.getAccessLevel().name(),
                Timestamp.from(updatedToken.getExpiryDate()),
                updatedToken.getLastUsed() != null ? Timestamp.from(updatedToken.getLastUsed()) : null,
                id);

        if (rowsAffected > 0) {
            return findById(id);
        }
        return Optional.empty();
    }

    public Optional<MagicLinkToken> updateLastUsed(long id, Instant lastUsed) {
        String sql = "UPDATE magic_link_tokens SET last_used_at = ? WHERE id = ?";
        
        int rowsAffected = jdbcTemplate.update(sql, Timestamp.from(lastUsed), id);
        
        if (rowsAffected > 0) {
            return findById(id);
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<MagicLinkToken> findById(long id) {
        String sql = """
            SELECT id, token_hash, access_level, expiry_date, created_at, last_used_at
            FROM magic_link_tokens 
            WHERE id = ?
            """;

        List<MagicLinkToken> results = jdbcTemplate.query(sql, new MagicLinkTokenRowMapper(), id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Transactional(readOnly = true)
    public Optional<MagicLinkToken> findByTokenHash(String tokenHash) {
        String sql = """
            SELECT id, token_hash, access_level, expiry_date, created_at, last_used_at
            FROM magic_link_tokens 
            WHERE token_hash = ?
            """;

        List<MagicLinkToken> results = jdbcTemplate.query(sql, new MagicLinkTokenRowMapper(), tokenHash);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Transactional(readOnly = true)
    public List<MagicLinkToken> findByUser(User user) {
        String sql = """
            SELECT id, token_hash, access_level, expiry_date, created_at, last_used_at
            FROM magic_link_tokens 
            WHERE user_id = ?
            ORDER BY created_at DESC
            """;

        return jdbcTemplate.query(sql, new MagicLinkTokenRowMapper(), user.getId());
    }

    @Transactional(readOnly = true)
    public List<MagicLinkToken> findExpiredTokens() {
        String sql = """
            SELECT id, token_hash, access_level, expiry_date, created_at, last_used_at
            FROM magic_link_tokens 
            WHERE expiry_date < ?
            """;

        return jdbcTemplate.query(sql, new MagicLinkTokenRowMapper(), Timestamp.from(Instant.now()));
    }

    public void delete(long id) {
        String sql = "DELETE FROM magic_link_tokens WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    public void deleteExpiredTokens() {
        String sql = "DELETE FROM magic_link_tokens WHERE expiry_date < ?";
        jdbcTemplate.update(sql, Timestamp.from(Instant.now()));
    }

    public void recordUsage(long tokenId, String endpoint, String ipAddress) {
        String sql = """
            INSERT INTO magic_link_tokens_usages (token_id, at, endpoint, ip)
            VALUES (?, ?, ?, ?)
            """;

        jdbcTemplate.update(sql, tokenId, Timestamp.from(Instant.now()), endpoint, ipAddress);
    }

    private static class MagicLinkTokenRowMapper implements RowMapper<MagicLinkToken> {
        @Override
        public MagicLinkToken mapRow(ResultSet rs, int rowNum) throws SQLException {
            long id = rs.getLong("id");
            String tokenHash = rs.getString("token_hash");
            MagicLinkAccessLevel accessLevel = MagicLinkAccessLevel.valueOf(rs.getString("access_level"));
            Instant expiryDate = rs.getTimestamp("expiry_date").toInstant();
            Instant createdAt = rs.getTimestamp("created_at").toInstant();
            
            Timestamp lastUsedTimestamp = rs.getTimestamp("last_used_at");
            Instant lastUsed = lastUsedTimestamp != null ? lastUsedTimestamp.toInstant() : null;
            
            boolean isUsed = lastUsed != null;

            return new MagicLinkToken(id, tokenHash, accessLevel, expiryDate, createdAt, lastUsed, isUsed);
        }
    }
}
