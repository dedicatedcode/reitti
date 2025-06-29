package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.ProcessedVisit;
import com.dedicatedcode.reitti.model.SignificantPlace;
import com.dedicatedcode.reitti.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProcessedVisitJdbcService {

    private final JdbcTemplate jdbcTemplate;
    private final SignificantPlaceJdbcService significantPlaceJdbcService;

    public ProcessedVisitJdbcService(JdbcTemplate jdbcTemplate, SignificantPlaceJdbcService significantPlaceJdbcService) {
        this.jdbcTemplate = jdbcTemplate;
        this.significantPlaceJdbcService = significantPlaceJdbcService;
    }

    private final RowMapper<ProcessedVisit> PROCESSED_VISIT_ROW_MAPPER = new RowMapper<>() {
        @Override
        public ProcessedVisit mapRow(ResultSet rs, int rowNum) throws SQLException {
            SignificantPlace place = significantPlaceJdbcService.findById(rs.getLong("place_id")).orElseThrow();
            Long processedVisitId = rs.getLong("id");

            return new ProcessedVisit(
                    processedVisitId,
                    place,
                    rs.getTimestamp("start_time").toInstant(),
                    rs.getTimestamp("end_time").toInstant(),
                    rs.getLong("duration_seconds"),
                    rs.getLong("version")
            );
        }
    };

    public List<ProcessedVisit> findAll() {
        String sql = "SELECT pv.* " +
                "FROM processed_visits pv " +
                "ORDER BY pv.start_time";
        return jdbcTemplate.query(sql, PROCESSED_VISIT_ROW_MAPPER);

    }
    public List<ProcessedVisit> findByUser(User user) {
        String sql = "SELECT pv.* " +
                "FROM processed_visits pv " +
                "WHERE pv.user_id = ? ORDER BY pv.start_time";
        return jdbcTemplate.query(sql, PROCESSED_VISIT_ROW_MAPPER, user.getId());
    }

    public List<ProcessedVisit> findByUserAndTimeOverlap(User user, Instant startTime, Instant endTime) {
        String sql = "SELECT pv.* " +
                "FROM processed_visits pv " +
                "WHERE pv.user_id = ? AND pv.start_time <= ? AND pv.end_time >= ?";
        return jdbcTemplate.query(sql, PROCESSED_VISIT_ROW_MAPPER, user.getId(),
                Timestamp.from(endTime), Timestamp.from(startTime));
    }

    public Optional<ProcessedVisit> findByUserAndId(User user, long id) {
        String sql = "SELECT pv.* " +
                "FROM processed_visits pv " +
                "WHERE pv.user_id = ? AND pv.id = ?";
        List<ProcessedVisit> results = jdbcTemplate.query(sql, PROCESSED_VISIT_ROW_MAPPER, user.getId(), id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<ProcessedVisit> findByUserAndStartTimeBeforeEqualAndEndTimeAfterEqual(User user, Instant endTime, Instant startTime) {
        String sql = "SELECT pv.* " +
                "FROM processed_visits pv " +
                "WHERE pv.user_id = ? AND pv.start_time <= ? AND pv.end_time >= ? ORDER BY start_time";
        return jdbcTemplate.query(sql, PROCESSED_VISIT_ROW_MAPPER, user.getId(),
                Timestamp.from(endTime), Timestamp.from(startTime));
    }

    public List<Object[]> findTopPlacesByStayTimeWithLimit(User user, long limit) {
        String sql = "SELECT sp.name, SUM(pv.duration_seconds), COUNT(pv), sp.latitude_centroid, sp.longitude_centroid " +
                "FROM processed_visits pv " +
                "JOIN significant_places sp ON pv.place_id = sp.id " +
                "WHERE pv.user_id = ? " +
                "GROUP BY sp.id, sp.name, sp.latitude_centroid, sp.longitude_centroid " +
                "ORDER BY SUM(pv.duration_seconds) DESC LIMIT ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new Object[]{
                rs.getString(1),
                rs.getLong(2),
                rs.getLong(3),
                rs.getDouble(4),
                rs.getDouble(5)
        }, user.getId(), limit);
    }

    public List<Object[]> findTopPlacesByStayTimeWithLimit(User user, Instant startTime, Instant endTime, long limit) {
        String sql = "SELECT sp.name, SUM(pv.duration_seconds), COUNT(pv), sp.latitude_centroid, sp.longitude_centroid " +
                "FROM processed_visits pv " +
                "JOIN significant_places sp ON pv.place_id = sp.id " +
                "WHERE pv.user_id = ? AND pv.start_time >= ? AND pv.end_time <= ? " +
                "GROUP BY sp.id, sp.name, sp.latitude_centroid, sp.longitude_centroid " +
                "ORDER BY SUM(pv.duration_seconds) DESC LIMIT ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new Object[]{
                rs.getString(1),
                rs.getLong(2),
                rs.getLong(3),
                rs.getDouble(4),
                rs.getDouble(5)
        }, user.getId(), Timestamp.from(startTime), Timestamp.from(endTime), limit);
    }

    public ProcessedVisit create(User user, ProcessedVisit visit) {
        String sql = "INSERT INTO processed_visits (user_id, start_time, end_time, duration_seconds, place_id, version) " +
                "VALUES (?, ?, ?, ?, ?, 1) RETURNING id";
        Long id = jdbcTemplate.queryForObject(sql, Long.class,
                user.getId(),
                Timestamp.from(visit.getStartTime()),
                Timestamp.from(visit.getEndTime()),
                visit.getDurationSeconds(),
                visit.getPlace() != null ? visit.getPlace().getId() : null
        );
        return visit.withId(id).withVersion(1);
    }

    public ProcessedVisit update(ProcessedVisit visit) {
        String sql = "UPDATE processed_visits SET start_time = ?, end_time = ?, duration_seconds = ?, place_id = ? WHERE id = ?";
        jdbcTemplate.update(sql,
                Timestamp.from(visit.getStartTime()),
                Timestamp.from(visit.getEndTime()),
                visit.getDurationSeconds(),
                visit.getPlace().getId(),
                visit.getId()
        );
        return visit;
    }

    public Optional<ProcessedVisit> findById(Long id) {
        String sql = "SELECT pv.* " +
                "FROM processed_visits pv " +
                "WHERE pv.id = ?";
        List<ProcessedVisit> results = jdbcTemplate.query(sql, PROCESSED_VISIT_ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public void deleteAll(List<ProcessedVisit> processedVisits) {
        if (processedVisits == null || processedVisits.isEmpty()) {
            return;
        }

        List<Long> ids = processedVisits.stream()
                .map(ProcessedVisit::getId)
                .toList();

        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        String sql = "DELETE FROM processed_visits WHERE id IN (" + placeholders + ")";

        jdbcTemplate.update(sql, ids.toArray());
    }

    public Optional<ProcessedVisit> findByUserAndStartTimeAndEndTimeAndPlace(User user, Instant startTime, Instant endTime, SignificantPlace place) {
        String sql = "SELECT pv.* " +
                "FROM processed_visits pv " +
                "WHERE pv.user_id = ? AND pv.start_time = ? AND pv.end_time = ? AND pv.place_id = ?";
        List<ProcessedVisit> results = jdbcTemplate.query(sql, PROCESSED_VISIT_ROW_MAPPER,
                user.getId(),
                Timestamp.from(startTime),
                Timestamp.from(endTime),
                place.getId());
        return Optional.ofNullable(results.isEmpty() ? null : results.getFirst());
    }

    public List<ProcessedVisit> bulkInsert(User user, List<ProcessedVisit> visitsToStore) {
        if (visitsToStore.isEmpty()) {
            return new ArrayList<>();
        }

        List<ProcessedVisit> result = new ArrayList<>();

        String sql = """
                INSERT INTO processed_visits (user_id, place_id, start_time, end_time, duration_seconds)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING;
                """;

        List<Object[]> batchArgs = visitsToStore.stream()
                .map(visit -> new Object[]{
                        user.getId(),
                        visit.getPlace().getId(),
                        Timestamp.from(visit.getStartTime()),
                        Timestamp.from(visit.getEndTime()),
                        visit.getDurationSeconds()})
                .collect(Collectors.toList());

        int[] updateCounts = jdbcTemplate.batchUpdate(sql, batchArgs);
        for (int i = 0; i < updateCounts.length; i++) {
            int updateCount = updateCounts[i];
            if (updateCount > 0) {
                Optional<ProcessedVisit> byUserAndStartTimeAndEndTimeAndPlace = this.findByUserAndStartTimeAndEndTimeAndPlace(user, visitsToStore.get(i).getStartTime(), visitsToStore.get(i).getEndTime(), visitsToStore.get(i).getPlace());
                byUserAndStartTimeAndEndTimeAndPlace.ifPresent(result::add);
            }
        }
        return result;
    }

    public void deleteAll() {
        String sql = "DELETE FROM processed_visits";
        jdbcTemplate.update(sql);
    }

    public void deleteAllForUser(User user) {
        String sql = "DELETE FROM processed_visits WHERE user_id = ?";
        jdbcTemplate.update(sql, user.getId());
    }

}
