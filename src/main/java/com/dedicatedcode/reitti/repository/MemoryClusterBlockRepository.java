package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.memory.MemoryClusterBlock;
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

    public void save(MemoryClusterBlock cluster) {
        String sql = "INSERT INTO memory_block_cluster (block_id, trip_ids) VALUES (?, ?::jsonb) " +
                     "ON CONFLICT (block_id) DO UPDATE SET trip_ids = EXCLUDED.trip_ids";
        try {
            String tripIdsJson = objectMapper.writeValueAsString(cluster.getTripIds());
            jdbcTemplate.update(sql, cluster.getBlockId(), tripIdsJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save MemoryClusterBlock", e);
        }
    }

    public Optional<MemoryClusterBlock> findByBlockId(Long blockId) {
        String sql = "SELECT block_id, trip_ids FROM memory_block_cluster WHERE block_id = ?";
        List<MemoryClusterBlock> results = jdbcTemplate.query(sql, new MemoryClusterBlockRowMapper(), blockId);
        return results.stream().findFirst();
    }

    public void deleteByBlockId(Long blockId) {
        String sql = "DELETE FROM memory_block_cluster WHERE block_id = ?";
        jdbcTemplate.update(sql, blockId);
    }

    private class MemoryClusterBlockRowMapper implements RowMapper<MemoryClusterBlock> {
        @Override
        public MemoryClusterBlock mapRow(ResultSet rs, int rowNum) throws SQLException {
            Long blockId = rs.getLong("block_id");
            String tripIdsJson = rs.getString("trip_ids");
            List<Long> tripIds = null;
            try {
                tripIds = objectMapper.readValue(tripIdsJson, new TypeReference<List<Long>>() {});
            } catch (Exception e) {
                throw new SQLException("Failed to parse trip_ids JSON", e);
            }
            return new MemoryClusterBlock(blockId, tripIds);
        }
    }
}
