package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.TransportModeSegment;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@Transactional
public class PreviewTripJdbcService {

    private final JdbcTemplate jdbcTemplate;
    private final PreviewProcessedVisitJdbcService previewProcessedVisitJdbcService;
    private final PreviewTripTransportModeJdbcService previewTripTransportModeJdbcService;

    public PreviewTripJdbcService(JdbcTemplate jdbcTemplate, PreviewProcessedVisitJdbcService previewProcessedVisitJdbcService, PreviewTripTransportModeJdbcService previewTripTransportModeJdbcService) {
        this.jdbcTemplate = jdbcTemplate;
        this.previewProcessedVisitJdbcService = previewProcessedVisitJdbcService;
        this.previewTripTransportModeJdbcService = previewTripTransportModeJdbcService;
    }

    private record RawTripRow(
            Long id,
            Instant startTime,
            Instant endTime,
            Long durationSeconds,
            Double estimatedDistanceMeters,
            Double travelledDistanceMeters,
            Long startVisitId,
            Long endVisitId,
            Long version
    ) {}

    private final RowMapper<RawTripRow> RAW_TRIP_ROW_MAPPER = (rs, rowNum) -> new RawTripRow(
            rs.getLong("id"),
            rs.getTimestamp("start_time").toInstant(),
            rs.getTimestamp("end_time").toInstant(),
            rs.getLong("duration_seconds"),
            rs.getDouble("estimated_distance_meters"),
            rs.getDouble("travelled_distance_meters"),
            rs.getLong("start_visit_id"),
            rs.getLong("end_visit_id"),
            rs.getLong("version")
    );

    private List<Trip> assembleTrips(List<RawTripRow> rawRows) {
        if (rawRows.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> tripIds = rawRows.stream().map(RawTripRow::id).toList();
        List<Long> visitIds = rawRows.stream()
                .flatMap(r -> Stream.of(r.startVisitId(), r.endVisitId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, ProcessedVisit> visitsById = previewProcessedVisitJdbcService.findByIds(visitIds);
        Map<Long, List<TransportModeSegment>> segmentsByTripId = previewTripTransportModeJdbcService.findByTripIds(tripIds);

        return rawRows.stream().map(r -> {
            ProcessedVisit startVisit = visitsById.get(r.startVisitId());
            ProcessedVisit endVisit = visitsById.get(r.endVisitId());
            List<TransportModeSegment> segments = segmentsByTripId.getOrDefault(r.id(), Collections.emptyList());
            return new Trip(
                    r.id(),
                    r.startTime(),
                    r.endTime(),
                    r.durationSeconds(),
                    r.estimatedDistanceMeters(),
                    r.travelledDistanceMeters(),
                    segments,
                    startVisit,
                    endVisit,
                    null,
                    r.version()
            );
        }).toList();
    }

    public Optional<Trip> findById(Long id) {
        String sql = "SELECT t.* FROM preview_trips t WHERE t.id = ?";
        List<RawTripRow> results = jdbcTemplate.query(sql, RAW_TRIP_ROW_MAPPER, id);
        List<Trip> trips = assembleTrips(results);
        return trips.isEmpty() ? Optional.empty() : Optional.of(trips.getFirst());
    }

    public List<Trip> findByUserAndTimeOverlap(User user, String previewId, Instant startTime, Instant endTime) {
        String sql = "SELECT t.* " +
                "FROM preview_trips t " +
                "WHERE t.user_id = ? " +
                "AND t.preview_id = ? " +
                "AND ((t.start_time <= ? AND t.end_time >= ?) OR " +
                "(t.start_time >= ? AND t.start_time <= ?) OR " +
                "(t.end_time >= ? AND t.end_time <= ?)) " +
                "ORDER BY start_time";
        List<RawTripRow> results = jdbcTemplate.query(sql, RAW_TRIP_ROW_MAPPER, user.getId(),
                previewId,
                Timestamp.from(endTime), Timestamp.from(startTime),
                Timestamp.from(startTime), Timestamp.from(endTime),
                Timestamp.from(startTime), Timestamp.from(endTime));
        return assembleTrips(results);
    }

    public List<Trip> bulkInsert(User user, String previewId, List<Trip> tripsToInsert) {
        if (tripsToInsert.isEmpty()) {
            return tripsToInsert;
        }

        String sql = """
            INSERT INTO preview_trips (user_id, start_visit_id, end_visit_id, start_time, end_time,
                              duration_seconds, estimated_distance_meters, travelled_distance_meters, version, preview_id, preview_created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now()) ON CONFLICT DO NOTHING RETURNING id;
            """;

        List<Trip> result = new ArrayList<>();
        for (Trip trip : tripsToInsert) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            int updated = jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, user.getId());
                ps.setLong(2, trip.getStartVisit().getId());
                ps.setLong(3, trip.getEndVisit().getId());
                ps.setTimestamp(4, Timestamp.from(trip.getStartTime()));
                ps.setTimestamp(5, Timestamp.from(trip.getEndTime()));
                ps.setLong(6, trip.getDurationSeconds());
                ps.setObject(7, trip.getEstimatedDistanceMeters());
                ps.setObject(8, trip.getTravelledDistanceMeters());
                ps.setLong(9, trip.getVersion());
                ps.setString(10, previewId);
                return ps;
            }, keyHolder);

            Trip persisted = trip;
            if (updated > 0 && keyHolder.getKey() != null) {
                Long id = keyHolder.getKey().longValue();
                persisted = trip.withId(id);
                previewTripTransportModeJdbcService.bulkInsert(id, persisted.getSegments());
            }
            result.add(persisted);
        }
        return result;
    }

    public void deleteAll(List<Trip> existingTrips) {
        if (existingTrips == null || existingTrips.isEmpty()) {
            return;
        }

        List<Long> ids = existingTrips.stream()
                .map(Trip::getId)
                .toList();

        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        String sql = "DELETE FROM preview_trips WHERE id IN (" + placeholders + ")";

        jdbcTemplate.update(sql, ids.toArray());
    }
}
