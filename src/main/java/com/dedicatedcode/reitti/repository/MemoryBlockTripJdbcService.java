package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.memory.MemoryBlockTrip;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MemoryBlockTripJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public MemoryBlockTripJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<MemoryBlockTrip> MEMORY_BLOCK_TRIP_ROW_MAPPER = (rs, rowNum) -> new MemoryBlockTrip(
            rs.getLong("block_id"),
            rs.getLong("trip_id")
    );

    public MemoryBlockTrip create(MemoryBlockTrip blockTrip) {
        jdbcTemplate.update(
                "INSERT INTO memory_block_trip (block_id, trip_id) VALUES (?, ?)",
                blockTrip.getBlockId(),
                blockTrip.getTripId()
        );
        return blockTrip;
    }

    public Optional<MemoryBlockTrip> findByBlockId(Long blockId) {
        List<MemoryBlockTrip> results = jdbcTemplate.query(
                "SELECT * FROM memory_block_trip WHERE block_id = ?",
                MEMORY_BLOCK_TRIP_ROW_MAPPER,
                blockId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public void delete(Long blockId) {
        jdbcTemplate.update("DELETE FROM memory_block_trip WHERE block_id = ?", blockId);
    }
}
