package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.geo.TransportMode;
import com.dedicatedcode.reitti.model.geo.TransportModeSegment;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class TripTransportModeJdbcService {

    private final JdbcTemplate jdbcTemplate;

    public TripTransportModeJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<Long, List<TransportModeSegment>> findByTripIds(User user, List<Long> tripIds) {
        if (tripIds == null || tripIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = String.join(",", tripIds.stream().map(_ -> "?").toList());
        String sql = "SELECT tm.trip_id, tm.offset_seconds, tm.duration_in_seconds, tm.transportation_mode, tm.distance_meters " +
                "FROM trip_transport_modes tm JOIN trips t ON t.id = tm.trip_id " +
                "WHERE tm.trip_id IN (" + placeholders + ") AND t.user_id = ? ORDER BY tm.trip_id, tm.offset_seconds";
        Map<Long, List<TransportModeSegment>> result = new HashMap<>();
        List<Object> args = new ArrayList<>(tripIds);
        args.add(user.getId());
        jdbcTemplate.query(sql, rs -> {
            Long tripId = rs.getLong("trip_id");
            TransportModeSegment segment = new TransportModeSegment(
                    TransportMode.valueOf(rs.getString("transportation_mode")),
                    rs.getLong("offset_seconds"),
                    rs.getLong("duration_in_seconds"),
                    rs.getDouble("distance_meters")
            );
            result.computeIfAbsent(tripId, _ -> new ArrayList<>()).add(segment);
        }, args.toArray());
        return result;
    }

    public void bulkInsert(User user, Long tripId, List<TransportModeSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return;
        }
        String sql = """
            INSERT INTO trip_transport_modes (trip_id, offset_seconds, duration_in_seconds, transportation_mode, distance_meters)
            SELECT ?, ?, ?, ?, ?
            WHERE EXISTS (SELECT 1 FROM trips WHERE id = ? AND user_id = ?)
            """;
        List<Object[]> batchArgs = segments.stream()
                .map(s -> new Object[]{tripId, s.offsetSeconds(), s.durationSeconds(), s.mode().name(), s.distanceMeters(), tripId, user.getId()})
                .collect(Collectors.toList());
        jdbcTemplate.batchUpdate(sql, batchArgs);
    }

    public void deleteByTripId(User user, Long tripId) {
        jdbcTemplate.update("DELETE FROM trip_transport_modes tm " +
                "USING trips t WHERE tm.trip_id = t.id AND tm.trip_id = ? AND t.user_id = ?", tripId, user.getId());
    }
}
