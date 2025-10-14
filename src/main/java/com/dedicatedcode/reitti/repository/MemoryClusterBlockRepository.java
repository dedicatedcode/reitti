package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.memory.MemoryClusterBlock;
import com.dedicatedcode.reitti.model.security.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class MemoryClusterBlockRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MemoryClusterBlockRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(User user, MemoryClusterBlock cluster) {
        String sql = "INSERT INTO memory_block_cluster (block_id, trip_ids, user_id, title, description) VALUES (?, ?::jsonb, ?, ?, ?) " +
                     "ON CONFLICT (block_id) DO UPDATE SET trip_ids = EXCLUDED.trip_ids, title = EXCLUDED.title, description = EXCLUDED.description";
        try {
            String tripIdsJson = objectMapper.writeValueAsString(cluster.getTripIds());
            jdbcTemplate.update(sql, cluster.getBlockId(), tripIdsJson, user.getId(), cluster.getTitle(), cluster.getDescription());
        } catch (Exception e) {
            throw new RuntimeException("Failed to save MemoryClusterBlock", e);
        }
    }

    public Optional<MemoryClusterBlock> findByBlockId(User user, Long blockId) {
        String sql = "SELECT block_id, trip_ids, title, description FROM memory_block_cluster WHERE block_id = ? AND user_id = ?";
        List<MemoryClusterBlock> results = jdbcTemplate.query(sql, new MemoryClusterBlockRowMapper(), blockId, user.getId());
        return results.stream().findFirst();
    }

    public void deleteByBlockId(User user, Long blockId) {
        String sql = "DELETE FROM memory_block_cluster WHERE block_id = ? AND user_id = ?";
        jdbcTemplate.update(sql, blockId, user.getId());
    }

    private class MemoryClusterBlockRowMapper implements RowMapper<MemoryClusterBlock> {
        @Override
        public MemoryClusterBlock mapRow(ResultSet rs, int rowNum) throws SQLException {
            Long blockId = rs.getLong("block_id");
            String tripIdsJson = rs.getString("trip_ids");
            List<Long> tripIds;
            try {
                tripIds = objectMapper.readValue(tripIdsJson, new TypeReference<>() {
                });
            } catch (Exception e) {
                throw new SQLException("Failed to parse trip_ids JSON", e);
            }
            String title = rs.getString("title");
            String description = rs.getString("description");
            return new MemoryClusterBlock(blockId, tripIds, title, description);
        }
    }
}
