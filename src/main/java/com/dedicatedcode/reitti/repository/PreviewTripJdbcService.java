package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class PreviewTripJdbcService {

    private final JdbcTemplate jdbcTemplate;
    private final PreviewProcessedVisitJdbcService previewProcessedVisitJdbcService;
    public PreviewTripJdbcService(JdbcTemplate jdbcTemplate, PreviewProcessedVisitJdbcService previewProcessedVisitJdbcService) {
        this.jdbcTemplate = jdbcTemplate;
        this.previewProcessedVisitJdbcService = previewProcessedVisitJdbcService;
    }

    private final RowMapper<Trip> TRIP_ROW_MAPPER = new RowMapper<>() {
        @Override
        public Trip mapRow(ResultSet rs, int rowNum) throws SQLException {
            ProcessedVisit startVisit = previewProcessedVisitJdbcService.findById(rs.getLong("start_visit_id")).orElseThrow();
            ProcessedVisit endVisit = previewProcessedVisitJdbcService.findById(rs.getLong("end_visit_id")).orElseThrow();
            return new Trip(
                    rs.getLong("id"),
                    rs.getTimestamp("start_time").toInstant(),
                    rs.getTimestamp("end_time").toInstant(),
                    rs.getLong("duration_seconds"),
                    rs.getDouble("estimated_distance_meters"),
                    rs.getDouble("travelled_distance_meters"),
                    rs.getString("transport_mode_inferred"),
                    startVisit,
                    endVisit,
                    rs.getLong("version")
            );
        }
    };

    public List<Trip> findByUserAndTimeOverlap(User user, String previewId, Instant startTime, Instant endTime) {
        String sql = "SELECT t.* " +
                "FROM preview_trips t " +
                "WHERE t.user_id = ? " +
                "AND t.preview_id = ? " +
                "AND ((t.start_time <= ? AND t.end_time >= ?) OR " +
                "(t.start_time >= ? AND t.start_time <= ?) OR " +
                "(t.end_time >= ? AND t.end_time <= ?)) " +
                "ORDER BY start_time";
        return jdbcTemplate.query(sql, TRIP_ROW_MAPPER, user.getId(),
                previewId,
                Timestamp.from(endTime), Timestamp.from(startTime),
                Timestamp.from(startTime), Timestamp.from(endTime),
                Timestamp.from(startTime), Timestamp.from(endTime));
    }

    public void bulkInsert(User user, String previewId, List<Trip> tripsToInsert) {
        if (tripsToInsert.isEmpty()) {
            return;
        }
        
        String sql = """
            INSERT INTO preview_trips (user_id, start_visit_id, end_visit_id, start_time, end_time,
                              duration_seconds, estimated_distance_meters, travelled_distance_meters, transport_mode_inferred, version, preview_id, preview_created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now()) ON CONFLICT DO NOTHING;
            """;
        
        List<Object[]> batchArgs = tripsToInsert.stream()
            .map(trip -> new Object[]{
                    user.getId(),
                trip.getStartVisit().getId(),
                trip.getEndVisit().getId(),
                Timestamp.from(trip.getStartTime()),
                Timestamp.from(trip.getEndTime()),
                trip.getDurationSeconds(),
                trip.getEstimatedDistanceMeters(),
                trip.getTravelledDistanceMeters(),
                trip.getTransportModeInferred(),
                trip.getVersion(),
                previewId
            })
            .collect(Collectors.toList());
        
        jdbcTemplate.batchUpdate(sql, batchArgs);
    }

}
