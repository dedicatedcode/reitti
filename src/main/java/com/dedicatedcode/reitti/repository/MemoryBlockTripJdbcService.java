package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.memory.MemoryBlockTrip;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class MemoryBlockTripJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public MemoryBlockTripJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<MemoryBlockTrip> MEMORY_BLOCK_TRIP_ROW_MAPPER = (rs, rowNum) -> {
        Long originalId = rs.getLong("original_trip_id");
        if (rs.wasNull()) {
            originalId = null;
        }
        
        Double estimatedDistance = rs.getDouble("estimated_distance_meters");
        if (rs.wasNull()) {
            estimatedDistance = null;
        }
        
        Double travelledDistance = rs.getDouble("travelled_distance_meters");
        if (rs.wasNull()) {
            travelledDistance = null;
        }
        
        Double startLat = rs.getDouble("start_latitude");
        if (rs.wasNull()) {
            startLat = null;
        }
        
        Double startLon = rs.getDouble("start_longitude");
        if (rs.wasNull()) {
            startLon = null;
        }
        
        Double endLat = rs.getDouble("end_latitude");
        if (rs.wasNull()) {
            endLat = null;
        }
        
        Double endLon = rs.getDouble("end_longitude");
        if (rs.wasNull()) {
            endLon = null;
        }
        
        return new MemoryBlockTrip(
            rs.getLong("block_id"),
            originalId,
            rs.getTimestamp("start_time").toInstant(),
            rs.getTimestamp("end_time").toInstant(),
            rs.getLong("duration_seconds"),
            estimatedDistance,
            travelledDistance,
            rs.getString("transport_mode_inferred"),
            rs.getString("start_place_name"),
            startLat,
            startLon,
            rs.getString("end_place_name"),
            endLat,
            endLon
        );
    };

    public MemoryBlockTrip create(MemoryBlockTrip blockTrip) {
        jdbcTemplate.update(
                "INSERT INTO memory_block_trip (block_id, original_trip_id, start_time, end_time, " +
                "duration_seconds, estimated_distance_meters, travelled_distance_meters, transport_mode_inferred, " +
                "start_place_name, start_latitude, start_longitude, end_place_name, end_latitude, end_longitude) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                blockTrip.getBlockId(),
                blockTrip.getOriginalTripId(),
                Timestamp.from(blockTrip.getStartTime()),
                Timestamp.from(blockTrip.getEndTime()),
                blockTrip.getDurationSeconds(),
                blockTrip.getEstimatedDistanceMeters(),
                blockTrip.getTravelledDistanceMeters(),
                blockTrip.getTransportModeInferred(),
                blockTrip.getStartPlaceName(),
                blockTrip.getStartLatitude(),
                blockTrip.getStartLongitude(),
                blockTrip.getEndPlaceName(),
                blockTrip.getEndLatitude(),
                blockTrip.getEndLongitude()
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
