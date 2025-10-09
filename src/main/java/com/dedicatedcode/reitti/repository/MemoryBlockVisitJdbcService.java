package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.memory.MemoryBlockVisit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class MemoryBlockVisitJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public MemoryBlockVisitJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<MemoryBlockVisit> MEMORY_BLOCK_VISIT_ROW_MAPPER = (rs, rowNum) -> {
        Long originalId = rs.getLong("original_processed_visit_id");
        if (rs.wasNull()) {
            originalId = null;
        }
        
        return new MemoryBlockVisit(
            rs.getLong("block_id"),
            originalId,
            rs.getString("place_name"),
            rs.getString("place_address"),
            rs.getDouble("latitude"),
            rs.getDouble("longitude"),
            rs.getTimestamp("start_time").toInstant(),
            rs.getTimestamp("end_time").toInstant(),
            rs.getLong("duration_seconds")
        );
    };

    public MemoryBlockVisit create(MemoryBlockVisit blockVisit) {
        jdbcTemplate.update(
                "INSERT INTO memory_block_visit (block_id, original_processed_visit_id, place_name, place_address, " +
                "latitude, longitude, start_time, end_time, duration_seconds) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                blockVisit.getBlockId(),
                blockVisit.getOriginalProcessedVisitId(),
                blockVisit.getPlaceName(),
                blockVisit.getPlaceAddress(),
                blockVisit.getLatitude(),
                blockVisit.getLongitude(),
                Timestamp.from(blockVisit.getStartTime()),
                Timestamp.from(blockVisit.getEndTime()),
                blockVisit.getDurationSeconds()
        );
        return blockVisit;
    }

    public Optional<MemoryBlockVisit> findByBlockId(Long blockId) {
        List<MemoryBlockVisit> results = jdbcTemplate.query(
                "SELECT * FROM memory_block_visit WHERE block_id = ?",
                MEMORY_BLOCK_VISIT_ROW_MAPPER,
                blockId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public void delete(Long blockId) {
        jdbcTemplate.update("DELETE FROM memory_block_visit WHERE block_id = ?", blockId);
    }
}
