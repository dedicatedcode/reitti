package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.memory.MemoryBlockTrip;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MemoryBlockTripRepository {

    private final JdbcClient jdbcClient;

    public MemoryBlockTripRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private static final RowMapper<MemoryBlockTrip> MEMORY_BLOCK_TRIP_ROW_MAPPER = (rs, rowNum) -> new MemoryBlockTrip(
            rs.getLong("block_id"),
            rs.getLong("trip_id")
    );

    public MemoryBlockTrip create(MemoryBlockTrip blockTrip) {
        jdbcClient.sql("""
                INSERT INTO memory_block_trip (block_id, trip_id)
                VALUES (:blockId, :tripId)
                """)
                .param("blockId", blockTrip.getBlockId())
                .param("tripId", blockTrip.getTripId())
                .update();

        return blockTrip;
    }

    public Optional<MemoryBlockTrip> findByBlockId(Long blockId) {
        return jdbcClient.sql("SELECT * FROM memory_block_trip WHERE block_id = :blockId")
                .param("blockId", blockId)
                .query(MEMORY_BLOCK_TRIP_ROW_MAPPER)
                .optional();
    }

    public void delete(Long blockId) {
        jdbcClient.sql("DELETE FROM memory_block_trip WHERE block_id = :blockId")
                .param("blockId", blockId)
                .update();
    }
}
