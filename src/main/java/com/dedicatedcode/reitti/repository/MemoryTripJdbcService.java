package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.memory.MemoryTrip;
import com.dedicatedcode.reitti.model.memory.MemoryVisit;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.List;

@Service
public class MemoryTripJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public MemoryTripJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public MemoryTrip save(User user, MemoryTrip memoryTrip, Long memoryBlockId, Long originalId, Long startVisitId, Long endVisitId) {
        String sql = """
            INSERT INTO memory_trips (id, user_id, original_id, memory_block_id, start_visit_id, end_visit_id, start_time, end_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

        Long generatedId = jdbcTemplate.queryForObject(sql, Long.class,
            memoryTrip.getId(),
            user.getId(),
            originalId,
            memoryBlockId,
            startVisitId,
            endVisitId,
            memoryTrip.getStartTime(),
            memoryTrip.getEndTime()
        );

        return memoryTrip.withId(generatedId);
    }

    public List<MemoryTrip> findByMemoryBlockId(Long memoryBlockId) {
        String sql = """
            SELECT mt.id, mt.start_time, mt.end_time,
                           sv.id as start_visit_id, sv.name as start_visit_name, sv.start_time as start_visit_start_time, sv.original_id as start_visit_original_id,
                   sv.end_time as start_visit_end_time, sv.latitude_centroid as start_visit_lat, sv.longitude_centroid as start_visit_lon, sv.timezone as start_visit_timezone,
                           ev.id as end_visit_id, ev.name as end_visit_name, ev.start_time as end_visit_start_time, ev.original_id as end_visit_original_id,
                   ev.end_time as end_visit_end_time, ev.latitude_centroid as end_visit_lat, ev.longitude_centroid as end_visit_lon, ev.timezone as end_visit_timezone
            FROM memory_trips mt
            LEFT JOIN memory_visits sv ON mt.start_visit_id = sv.id
            LEFT JOIN memory_visits ev ON mt.end_visit_id = ev.id
            WHERE mt.memory_block_id = ?
            ORDER BY mt.start_time
            """;
        
        return jdbcTemplate.query(sql, new MemoryTripRowMapper(), memoryBlockId);
    }

    public List<MemoryTrip> findByUserAndMemoryId(User user, Long memoryId) {
        String sql = """
            SELECT mt.id, mt.start_time, mt.end_time,
                           sv.id as start_visit_id, sv.name as start_visit_name, sv.start_time as start_visit_start_time,
                           sv.original_id as start_visit_original_id,
                   sv.end_time as start_visit_end_time, sv.latitude_centroid as start_visit_lat, sv.longitude_centroid as start_visit_lon, sv.timezone as start_visit_timezone,
                           ev.id as end_visit_id, ev.name as end_visit_name, ev.start_time as end_visit_start_time, ev.original_id as end_visit_original_id,
                   ev.end_time as end_visit_end_time, ev.latitude_centroid as end_visit_lat, ev.longitude_centroid as end_visit_lon, ev.timezone as end_visit_timezone
            FROM memory_trips mt
            LEFT JOIN memory_visits sv ON mt.start_visit_id = sv.id
            LEFT JOIN memory_visits ev ON mt.end_visit_id = ev.id
            JOIN memory_block mb ON mt.memory_block_id = mb.id
            WHERE mt.user_id = ? AND mb.memory_id = ?
            ORDER BY mt.start_time
            """;
        
        return jdbcTemplate.query(sql, new MemoryTripRowMapper(), user.getId(), memoryId);
    }

    public void deleteByMemoryBlockId(Long memoryBlockId) {
        String sql = "DELETE FROM memory_trips WHERE memory_block_id = ?";
        jdbcTemplate.update(sql, memoryBlockId);
    }

    private static class MemoryTripRowMapper implements RowMapper<MemoryTrip> {
        @Override
        public MemoryTrip mapRow(ResultSet rs, int rowNum) throws SQLException {
            MemoryVisit startVisit = null;
            MemoryVisit endVisit = null;

            if (rs.getLong("start_visit_id") != 0) {
                startVisit = new MemoryVisit(
                        rs.getLong("start_visit_id"),
                        rs.getObject("start_visit_original_id") != null,
                        rs.getString("start_visit_name"),
                        rs.getTimestamp("start_visit_start_time").toInstant(),
                        rs.getTimestamp("start_visit_end_time").toInstant(),
                        rs.getDouble("start_visit_lat"),
                        rs.getDouble("start_visit_lon"),
                        ZoneId.of(rs.getString("start_visit_timezone")));
            }

            if (rs.getLong("end_visit_id") != 0) {
                endVisit = new MemoryVisit(
                        rs.getLong("end_visit_id"),
                        rs.getObject("end_visit_original_id") != null,
                        rs.getString("end_visit_name"),
                        rs.getTimestamp("end_visit_start_time").toInstant(),
                        rs.getTimestamp("end_visit_end_time").toInstant(),
                        rs.getDouble("end_visit_lat"),
                        rs.getDouble("end_visit_lon"),
                        ZoneId.of(rs.getString("end_visit_timezone")));
            }

            return new MemoryTrip(
                    rs.getLong("id"),
                    rs.getObject("original_id") != null,
                    startVisit,
                    endVisit,
                    rs.getTimestamp("start_time").toInstant(),
                    rs.getTimestamp("end_time").toInstant()
            );
        }
    }
}
