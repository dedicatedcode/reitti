package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.security.MagicLinkAccessLevel;
import com.dedicatedcode.reitti.model.security.MagicLinkToken;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;

    public MagicLinkJdbcService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @CacheEvict(value = "magic-links", allEntries = true)
    public MagicLinkToken create(User user, MagicLinkToken token) {
        String sql = """
            INSERT INTO magic_link_tokens (user_id, name, token_hash, access_level, expiry_date, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        Instant now = Instant.now();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setLong(1, user.getId());
            ps.setString(2, token.getName());
            ps.setString(3, token.getTokenHash());
            ps.setString(4, token.getAccessLevel().name());
            ps.setTimestamp(5, token.getExpiryDate() != null ? Timestamp.from(token.getExpiryDate()) : null);
            ps.setTimestamp(6, Timestamp.from(now));
            return ps;
        }, keyHolder);

        long id = keyHolder.getKey().longValue();
        return new MagicLinkToken(id, token.getName(), token.getTokenHash(), token.getAccessLevel(), token.getExpiryDate(), now, null, false);
    }

    @CacheEvict(value = "magic-links", allEntries = true)
    public Optional<MagicLinkToken> update(MagicLinkToken updatedToken) {
        String sql = """
            UPDATE magic_link_tokens
            SET token_hash = ?, access_level = ?, expiry_date = ?, last_used_at = ?
            WHERE id = ?
            """;

        int rowsAffected = jdbcTemplate.update(sql,
                updatedToken.getTokenHash(),
                updatedToken.getAccessLevel().name(),
                updatedToken.getExpiryDate() != null ? Timestamp.from(updatedToken.getExpiryDate()) : null,
                updatedToken.getLastUsed() != null ? Timestamp.from(updatedToken.getLastUsed()) : null,
                updatedToken.getId());

        if (rowsAffected > 0) {
            return findById(updatedToken.getId());
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "magic-links", key = "#id")
    public Optional<MagicLinkToken> findById(long id) {
        String sql = """
            SELECT id, name, token_hash, access_level, expiry_date, created_at, last_used_at
            FROM magic_link_tokens
            WHERE id = ?
            """;

        List<MagicLinkToken> results = jdbcTemplate.query(sql, new MagicLinkTokenRowMapper(), id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "magic-links", key = "'hash:' + #tokenHash")
    public Optional<MagicLinkToken> findByTokenHash(String tokenHash) {
        String sql = """
            SELECT id, name, token_hash, access_level, expiry_date, created_at, last_used_at
            FROM magic_link_tokens
            WHERE token_hash = ?
            """;

        List<MagicLinkToken> results = jdbcTemplate.query(sql, new MagicLinkTokenRowMapper(), tokenHash);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Transactional(readOnly = true)
    public Optional<MagicLinkToken> findByRawToken(String rawToken) {
        String sql = """
            SELECT id, name, token_hash, access_level, expiry_date, created_at, last_used_at
            FROM magic_link_tokens
            WHERE expiry_date IS NULL OR expiry_date > ?
            """;

        List<MagicLinkToken> activeTokens = jdbcTemplate.query(sql, new MagicLinkTokenRowMapper(), Timestamp.from(Instant.now()));
        
        for (MagicLinkToken token : activeTokens) {
            if (passwordEncoder.matches(rawToken, token.getTokenHash())) {
                return Optional.of(token);
            }
        }
        
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "magic-links", key = "'userId:' + #tokenId")
    public Optional<Long> findUserIdByToken(long tokenId) {
        String sql = """
            SELECT user_id
            FROM magic_link_tokens
            WHERE id = ?
            """;

        List<Long> results = jdbcTemplate.queryForList(sql, Long.class, tokenId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @CacheEvict(value = "magic-links", allEntries = true)
    public void updateLastUsed(long tokenId) {
        String sql = "UPDATE magic_link_tokens SET last_used_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, Timestamp.from(Instant.now()), tokenId);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "magic-links", key = "'user:' + #user.id")
    public List<MagicLinkToken> findByUser(User user) {
        String sql = """
            SELECT id, name, token_hash, access_level, expiry_date, created_at, last_used_at
            FROM magic_link_tokens
            WHERE user_id = ?
            ORDER BY created_at DESC
            """;

        return jdbcTemplate.query(sql, new MagicLinkTokenRowMapper(), user.getId());
    }

    @CacheEvict(value = "magic-links", allEntries = true)
    public void delete(long id) {
        String sql = "DELETE FROM magic_link_tokens WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    private static class MagicLinkTokenRowMapper implements RowMapper<MagicLinkToken> {
        @Override
        public MagicLinkToken mapRow(ResultSet rs, int rowNum) throws SQLException {
            long id = rs.getLong("id");
            String name = rs.getString("name");
            String tokenHash = rs.getString("token_hash");
            MagicLinkAccessLevel accessLevel = MagicLinkAccessLevel.valueOf(rs.getString("access_level"));
            Timestamp expiryTimestamp = rs.getTimestamp("expiry_date");
            Instant expiryDate = expiryTimestamp != null ? expiryTimestamp.toInstant() : null;
            Instant createdAt = rs.getTimestamp("created_at").toInstant();
            
            Timestamp lastUsedTimestamp = rs.getTimestamp("last_used_at");
            Instant lastUsed = lastUsedTimestamp != null ? lastUsedTimestamp.toInstant() : null;
            
            boolean isUsed = lastUsed != null;

            return new MagicLinkToken(id, name, tokenHash, accessLevel, expiryDate, createdAt, lastUsed, isUsed);
        }
    }
}
