package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.memory.MemoryVisit;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class MemoryVisitJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public MemoryVisitJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public MemoryVisit save(User user, MemoryVisit memoryVisit, Long memoryBlockId, Long originalId) {
        String sql = """
            INSERT INTO memory_visits (user_id, original_id, memory_block_id, name, start_time, end_time, latitude_centroid, longitude_centroid, timezone)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

        Long generatedId = jdbcTemplate.queryForObject(sql, Long.class,
                user.getId(),
                originalId,
                memoryBlockId,
                memoryVisit.getName(),
                Timestamp.from(memoryVisit.getStartTime()),
                Time.from(memoryVisit.getEndTime()),
                memoryVisit.getLatitudeCentroid(),
                memoryVisit.getLongitudeCentroid(),
                memoryVisit.getTimezone().getId()
        );

        return memoryVisit.withId(generatedId);
    }

    public List<MemoryVisit> findByMemoryBlockId(Long memoryBlockId) {
        String sql = """
            SELECT id, name, start_time, end_time, latitude_centroid, longitude_centroid, timezone
            FROM memory_visits
            WHERE memory_block_id = ?
            ORDER BY start_time
            """;
        
        return jdbcTemplate.query(sql, new MemoryVisitRowMapper(), memoryBlockId);
    }

    public void deleteByMemoryBlockId(Long memoryBlockId) {
        String sql = "DELETE FROM memory_visits WHERE memory_block_id = ?";
        jdbcTemplate.update(sql, memoryBlockId);
    }

    public void deleteById(Long id) {
        String sql = "DELETE FROM memory_visits WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    private static class MemoryVisitRowMapper implements RowMapper<MemoryVisit> {
        @Override
        public MemoryVisit mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new MemoryVisit(
                rs.getLong("id"),
                true, // connected - always true for persisted visits
                rs.getString("name"),
                rs.getTimestamp("start_time").toInstant(),
                rs.getTimestamp("end_time").toInstant(),
                rs.getDouble("latitude_centroid"),
                rs.getDouble("longitude_centroid"),
                ZoneId.of(rs.getString("timezone")));
        }
    }
}
