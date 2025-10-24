package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.memory.HeaderType;
import com.dedicatedcode.reitti.model.memory.Memory;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class MemoryJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public MemoryJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Memory> MEMORY_ROW_MAPPER = (rs, rowNum) -> new Memory(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("description"),
            rs.getTimestamp("start_date").toInstant(),
            rs.getTimestamp("end_date").toInstant(),
            HeaderType.valueOf(rs.getString("header_type")),
            rs.getString("header_image_url"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            rs.getLong("version")
    );

    public Memory create(User user, Memory memory) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO memory (user_id, title, description, start_date, end_date, header_type, header_image_url, created_at, updated_at, version) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, user.getId());
            ps.setString(2, memory.getTitle());
            ps.setString(3, memory.getDescription());
            ps.setObject(4, Timestamp.from(memory.getStartDate()));
            ps.setObject(5, Timestamp.from(memory.getEndDate()));
            ps.setString(6, memory.getHeaderType().name());
            ps.setString(7, memory.getHeaderImageUrl());
            ps.setTimestamp(8, Timestamp.from(memory.getCreatedAt()));
            ps.setTimestamp(9, Timestamp.from(memory.getUpdatedAt()));
            ps.setLong(10, memory.getVersion());
            return ps;
        }, keyHolder);

        Long id = (Long) keyHolder.getKeys().get("id");
        return memory.withId(id);
    }

    public Memory update(User user, Memory memory) {
        int updated = jdbcTemplate.update(
                "UPDATE memory " +
                "SET title = ?, description = ?, start_date = ?, end_date = ?, header_type = ?, header_image_url = ?, updated_at = ?, version = version + 1 " +
                "WHERE id = ? AND user_id = ? AND version = ?",
                memory.getTitle(),
                memory.getDescription(),
                Timestamp.from(memory.getStartDate()),
                Timestamp.from(memory.getEndDate()),
                memory.getHeaderType().name(),
                memory.getHeaderImageUrl(),
                Timestamp.from(memory.getUpdatedAt()),
                memory.getId(),
                user.getId(),
                memory.getVersion()
        );

        if (updated == 0) {
            throw new IllegalStateException("Memory not found or version mismatch");
        }

        return memory.withVersion(memory.getVersion() + 1);
    }

    public void delete(User user, Long memoryId) {
        jdbcTemplate.update(
                "DELETE FROM memory WHERE id = ? AND user_id = ?",
                memoryId,
                user.getId()
        );
    }

    public Optional<Memory> findById(User user, Long id) {
        List<Memory> results = jdbcTemplate.query(
                "SELECT * FROM memory WHERE id = ? AND user_id = ?",
                MEMORY_ROW_MAPPER,
                id,
                user.getId()
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public List<Memory> findAllByUser(User user) {
        return jdbcTemplate.query(
                "SELECT * FROM memory WHERE user_id = ? ORDER BY created_at DESC",
                MEMORY_ROW_MAPPER,
                user.getId()
        );
    }

    public List<Memory> findAllByUserAndYear(User user, int year) {
        return jdbcTemplate.query(
                "SELECT * FROM memory WHERE user_id = ? AND (extract(YEAR FROM start_date) = ? OR extract(YEAR FROM end_date) = ?) ORDER BY created_at DESC",
                MEMORY_ROW_MAPPER,
                user.getId(), year, year
        );
    }

    public List<Memory> findByDateRange(User user, Instant startDate, Instant endDate) {
        return jdbcTemplate.query(
                "SELECT * FROM memory " +
                "WHERE user_id = ? " +
                "AND (end_date <= ? AND start_date >= ?) " +
                "ORDER BY start_date DESC",
                MEMORY_ROW_MAPPER,
                user.getId(),
                endDate,
                startDate
        );
    }

    public List<Integer> findDistinctYears(User user) {
            String sql = "SELECT DISTINCT EXTRACT(YEAR FROM start_date) " +
                    "FROM memory " +
                    "WHERE user_id = ? " +
                    "ORDER BY EXTRACT(YEAR FROM start_date) DESC";
            return jdbcTemplate.queryForList(sql, Integer.class, user.getId());
        }

    public Optional<Long> getOwnerId(Memory memory) {
        return Optional.ofNullable(this.jdbcTemplate.queryForObject("SELECT user_id FROM memory WHERE id = ?", Long.class, memory.getId()));
    }
}
