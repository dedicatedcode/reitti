package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.geo.TransportModeSegment;
import com.dedicatedcode.reitti.model.geo.Trip;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

@Service
@Transactional
public class TripJdbcService {

    private final JdbcTemplate jdbcTemplate;
    private final ProcessedVisitJdbcService processedVisitJdbcService;
    private final TripTransportModeJdbcService tripTransportModeJdbcService;
    private final ObjectMapper objectMapper;

    public TripJdbcService(JdbcTemplate jdbcTemplate, ProcessedVisitJdbcService processedVisitJdbcService, TripTransportModeJdbcService tripTransportModeJdbcService, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.processedVisitJdbcService = processedVisitJdbcService;
        this.tripTransportModeJdbcService = tripTransportModeJdbcService;
        this.objectMapper = objectMapper;
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
            Map<String, Object> metadata,
            Long version
    ) {}

    private RawTripRow mapRawTripRow(ResultSet rs, int rowNum) throws SQLException {
        try {
            String metadataValue = rs.getString("metadata");
            Map<String, Object> metadata = metadataValue != null ? objectMapper.readValue(metadataValue, new TypeReference<>() {}) : null;
            return new RawTripRow(
                    rs.getLong("id"),
                    rs.getTimestamp("start_time").toInstant(),
                    rs.getTimestamp("end_time").toInstant(),
                    rs.getLong("duration_seconds"),
                    rs.getDouble("estimated_distance_meters"),
                    rs.getDouble("travelled_distance_meters"),
                    rs.getLong("start_visit_id"),
                    rs.getLong("end_visit_id"),
                    metadata,
                    rs.getLong("version")
            );
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }

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

        Map<Long, ProcessedVisit> visitsById = processedVisitJdbcService.findByIds(visitIds);
        Map<Long, List<TransportModeSegment>> segmentsByTripId = tripTransportModeJdbcService.findByTripIds(tripIds);

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
                    r.metadata(),
                    r.version()
            );
        }).toList();
    }

    public List<Trip> findByUser(User user) {
        String sql = "SELECT t.* FROM trips t WHERE t.user_id = ? ORDER BY start_time";
        List<RawTripRow> rawRows = jdbcTemplate.query(sql, this::mapRawTripRow, user.getId());
        return assembleTrips(rawRows);
    }

    public List<Trip> findByUserAndTimeOverlap(User user, Instant startTime, Instant endTime) {
        String sql = "SELECT t.* " +
                "FROM trips t " +
                "WHERE t.user_id = ? " +
                "AND ((t.start_time <= ? AND t.end_time >= ?) OR " +
                "(t.start_time >= ? AND t.start_time <= ?) OR " +
                "(t.end_time >= ? AND t.end_time <= ?)) " +
                "ORDER BY start_time";
        List<RawTripRow> rawRows = jdbcTemplate.query(sql, this::mapRawTripRow, user.getId(),
                Timestamp.from(endTime), Timestamp.from(startTime),
                Timestamp.from(startTime), Timestamp.from(endTime),
                Timestamp.from(startTime), Timestamp.from(endTime));
        return assembleTrips(rawRows);
    }

    public boolean existsByUserAndStartTimeAndEndTime(User user, Instant startTime, Instant endTime) {
        String sql = "SELECT COUNT(*) FROM trips WHERE user_id = ? AND start_time = ? AND end_time = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, user.getId(),
                Timestamp.from(startTime), Timestamp.from(endTime));
        return count != null && count > 0;
    }

    public List<Object[]> findTransportStatisticsByUser(User user) {
        String sql = "SELECT tm.transportation_mode, SUM(tm.distance_meters), SUM(tm.duration_in_seconds), COUNT(*) " +
                "FROM trip_transport_modes tm " +
                "JOIN trips t ON t.id = tm.trip_id " +
                "WHERE t.user_id = ? " +
                "GROUP BY tm.transportation_mode " +
                "ORDER BY SUM(tm.distance_meters) DESC";
        return jdbcTemplate.query(sql, (rs, _) -> new Object[]{
                rs.getString(1),
                rs.getDouble(2),
                rs.getLong(3),
                rs.getLong(4)
        }, user.getId());
    }

    public List<Object[]> findTransportStatisticsByUserAndTimeRange(User user, Instant startTime, Instant endTime) {
        String sql = "SELECT tm.transportation_mode, SUM(tm.distance_meters), SUM(tm.duration_in_seconds), COUNT(*) " +
                "FROM trip_transport_modes tm " +
                "JOIN trips t ON t.id = tm.trip_id " +
                "WHERE t.user_id = ? AND t.start_time >= ? AND t.end_time <= ? " +
                "GROUP BY tm.transportation_mode " +
                "ORDER BY SUM(tm.distance_meters) DESC";
        return jdbcTemplate.query(sql, (rs, _) -> new Object[]{
                rs.getString(1),
                rs.getDouble(2),
                rs.getLong(3),
                rs.getLong(4)
        }, user.getId(), Timestamp.from(startTime), Timestamp.from(endTime));
    }

    public Trip create(User user, Trip trip) {
        String sql = "INSERT INTO trips (user_id, start_time, end_time, duration_seconds, travelled_distance_meters, start_visit_id, end_visit_id, metadata, version) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, 1) RETURNING id";
        Long id = jdbcTemplate.queryForObject(sql, Long.class,
                user.getId(),
                Timestamp.from(trip.getStartTime()),
                Timestamp.from(trip.getEndTime()),
                trip.getDurationSeconds(),
                trip.getTravelledDistanceMeters(),
                trip.getStartVisit() != null ? trip.getStartVisit().getId() : null,
                trip.getEndVisit() != null ? trip.getEndVisit().getId() : null,
                asJson(trip.getMetadata())
        );
        Trip persisted = trip.withId(id);
        tripTransportModeJdbcService.bulkInsert(id, persisted.getSegments());
        return persisted;
    }

    public Trip update(Trip trip) {
        String sql = "UPDATE trips SET start_time = ?, end_time = ?, duration_seconds = ?, travelled_distance_meters = ?, start_visit_id = ?, end_visit_id = ?, metadata = ?::jsonb, version = ? WHERE id = ?";
        jdbcTemplate.update(sql,
                Timestamp.from(trip.getStartTime()),
                Timestamp.from(trip.getEndTime()),
                trip.getDurationSeconds(),
                trip.getTravelledDistanceMeters(),
                trip.getStartVisit() != null ? trip.getStartVisit().getId() : null,
                trip.getEndVisit() != null ? trip.getEndVisit().getId() : null,
                asJson(trip.getMetadata()),
                trip.getVersion() + 1,
                trip.getId()
        );
        tripTransportModeJdbcService.deleteByTripId(trip.getId());
        tripTransportModeJdbcService.bulkInsert(trip.getId(), trip.getSegments());
        return trip.withVersion(trip.getVersion() + 1);
    }

    public Optional<Trip> findById(Long id) {
        String sql = "SELECT t.* " +
                "FROM trips t " +
                "WHERE t.id = ?";
        List<RawTripRow> results = jdbcTemplate.query(sql, this::mapRawTripRow, id);
        List<Trip> trips = assembleTrips(results);
        return trips.isEmpty() ? Optional.empty() : Optional.of(trips.getFirst());
    }

    public List<Trip> bulkInsert(User user, List<Trip> tripsToInsert) {
        if (tripsToInsert.isEmpty()) {
            return tripsToInsert;
        }

        String sql = """
            INSERT INTO trips (user_id, start_visit_id, end_visit_id, start_time, end_time,
                              duration_seconds, estimated_distance_meters, travelled_distance_meters, metadata, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?) ON CONFLICT DO NOTHING RETURNING id;
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
                ps.setString(9, asJson(trip.getMetadata()));
                ps.setLong(10, trip.getVersion());
                return ps;
            }, keyHolder);

            Trip persisted = trip;
            if (updated > 0 && keyHolder.getKey() != null) {
                Long id = keyHolder.getKey().longValue();
                persisted = trip.withId(id);
                tripTransportModeJdbcService.bulkInsert(id, persisted.getSegments());
            }
            result.add(persisted);
        }
        return result;
    }

    public void deleteAll() {
        String sql = "DELETE FROM trips";
        jdbcTemplate.update(sql);
    }

    public void deleteAllForUser(User user) {
        String sql = "DELETE FROM trips WHERE user_id = ?";
        jdbcTemplate.update(sql, user.getId());
    }

    public List<Long> findIdsByUser(User user) {
        return jdbcTemplate.queryForList("SELECT id FROM trips WHERE user_id = ?", Long.class, user.getId());
    }

    @SuppressWarnings("DataFlowIssue")
    public long count() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM trips", Long.class);
    }

    @SuppressWarnings("DataFlowIssue")
    public long count(User user) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM trips WHERE user_id = ?", Long.class, user.getId());
    }

    public void deleteAll(List<Trip> existingTrips) {
        if (existingTrips == null || existingTrips.isEmpty()) {
            return;
        }

        List<Long> ids = existingTrips.stream()
                .map(Trip::getId)
                .toList();

        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        String sql = "DELETE FROM trips WHERE id IN (" + placeholders + ")";

        jdbcTemplate.update(sql, ids.toArray());
    }

    public void deleteFor(User user, List<SignificantPlace> placesToRemove) {
        if (placesToRemove == null || placesToRemove.isEmpty()) {
            return;
        }
        Long[] idList = placesToRemove.stream().map(SignificantPlace::getId).toArray(Long[]::new);
        this.jdbcTemplate.update("""
                                     DELETE FROM trips
                                            WHERE user_id = ?
                                              AND (start_visit_id IN (SELECT id FROM processed_visits WHERE place_id = ANY(?))
                                               OR end_visit_id IN (SELECT id FROM processed_visits WHERE place_id = ANY(?)))
                                     """,
                                 user.getId(),
                                 idList,
                                 idList);
    }


    private String asJson(Object value) {
        try {
            return this.objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }

}
