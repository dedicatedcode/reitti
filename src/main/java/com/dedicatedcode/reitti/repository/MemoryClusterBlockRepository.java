package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.memory.BlockType;
import com.dedicatedcode.reitti.model.memory.MemoryBlockText;
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
        String sql = "INSERT INTO memory_block_cluster (block_id, part_ids, user_id, title, description, type) VALUES (?, ?::jsonb, ?, ?, ?, ?) " +
                     "ON CONFLICT (block_id) DO UPDATE SET part_ids = EXCLUDED.part_ids, title = EXCLUDED.title, description = EXCLUDED.description, type = EXCLUDED.type";
        try {
            String tripIdsJson = objectMapper.writeValueAsString(cluster.getPartIds());
            jdbcTemplate.update(sql, cluster.getBlockId(), tripIdsJson, user.getId(), cluster.getTitle(), cluster.getDescription(), cluster.getType().name());
        } catch (Exception e) {
            throw new RuntimeException("Failed to save MemoryClusterBlock", e);
        }
    }

    public Optional<MemoryClusterBlock> findByBlockId(User user, Long blockId) {
        String sql = "SELECT block_id, part_ids, title, description, type FROM memory_block_cluster WHERE block_id = ? AND user_id = ?";
        List<MemoryClusterBlock> results = jdbcTemplate.query(sql, new MemoryClusterBlockRowMapper(), blockId, user.getId());
        return results.stream().findFirst();
    }

    public void deleteByBlockId(User user, Long blockId) {
        String sql = "DELETE FROM memory_block_cluster WHERE block_id = ? AND user_id = ?";
        jdbcTemplate.update(sql, blockId, user.getId());
    }

    public MemoryClusterBlock update(User user, MemoryClusterBlock cluster) {
        String sql = "UPDATE memory_block_cluster SET part_ids = ?::jsonb, title = ?, description = ?, type = ? WHERE block_id = ? AND user_id = ?";
        try {
            String tripIdsJson = objectMapper.writeValueAsString(cluster.getPartIds());
            this.jdbcTemplate.update(sql, tripIdsJson, cluster.getTitle(), cluster.getDescription(), cluster.getType().name(), cluster.getBlockId(), user.getId());
            return cluster;
        } catch (Exception e) {
            throw new RuntimeException("Failed to save MemoryClusterBlock", e);
        }
    }

    private class MemoryClusterBlockRowMapper implements RowMapper<MemoryClusterBlock> {
        @Override
        public MemoryClusterBlock mapRow(ResultSet rs, int rowNum) throws SQLException {
            Long blockId = rs.getLong("block_id");
            String tripIdsJson = rs.getString("part_ids");
            List<Long> tripIds;
            try {
                tripIds = objectMapper.readValue(tripIdsJson, new TypeReference<>() {
                });
            } catch (Exception e) {
                throw new SQLException("Failed to parse trip_ids JSON", e);
            }
            String title = rs.getString("title");
            String description = rs.getString("description");
            BlockType type = BlockType.valueOf(rs.getString("type"));
            return new MemoryClusterBlock(blockId, tripIds, title, description, type);
        }
    }
}
