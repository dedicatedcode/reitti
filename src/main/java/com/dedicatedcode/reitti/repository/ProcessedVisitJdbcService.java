package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.geo.ProcessedVisit;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.security.User;
import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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

    public List<ProcessedVisit> findByUser(User user) {
        String sql = "SELECT pv.* " +
                "FROM processed_visits pv " +
                "WHERE pv.user_id = ? ORDER BY pv.start_time";
        return jdbcTemplate.query(sql, PROCESSED_VISIT_ROW_MAPPER, user.getId());
    }

    public List<ProcessedVisit> findByUserAndTimeOverlap(User user, Instant startTime, Instant endTime) {
        String sql = "SELECT pv.* " +
                "FROM processed_visits pv " +
                "WHERE pv.user_id = ? AND pv.start_time <= ? AND pv.end_time >= ? ORDER BY pv.start_time";
        return jdbcTemplate.query(sql, PROCESSED_VISIT_ROW_MAPPER, user.getId(),
                Timestamp.from(endTime), Timestamp.from(startTime));
    }

    public Optional<ProcessedVisit> findFirstProcessedVisitBefore(User user, Instant time) {
        String sql = "SELECT pv.* " +
                "FROM processed_visits pv " +
                "WHERE pv.user_id = ? AND pv.end_time < ? ORDER BY pv.end_time DESC LIMIT 1";
        return jdbcTemplate.query(sql, PROCESSED_VISIT_ROW_MAPPER, user.getId(), Timestamp.from(time)).stream().findFirst();
    }

    public Optional<ProcessedVisit> findFirstProcessedVisitAfter(User user, Instant time) {
        String sql = "SELECT pv.* " +
                "FROM processed_visits pv " +
                "WHERE pv.user_id = ? AND pv.start_time > ? ORDER BY pv.start_time LIMIT 1";
        return jdbcTemplate.query(sql, PROCESSED_VISIT_ROW_MAPPER, user.getId(), Timestamp.from(time)).stream().findFirst();

    }
    public Optional<ProcessedVisit> findByUserAndId(User user, long id) {
        String sql = "SELECT pv.* " +
                "FROM processed_visits pv " +
                "WHERE pv.user_id = ? AND pv.id = ? ORDER BY pv.start_time";
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

    public List<ProcessedVisit> bulkInsert(User user, List<ProcessedVisit> visitsToStore) {
        if (visitsToStore.isEmpty()) {
            return new ArrayList<>();
        }

        List<ProcessedVisit> result = new ArrayList<>();

        // 1. Build the multi-row INSERT statement structure
        String valuePlaceholder = "(?, ?, ?, ?, ?)";
        String valuesPlaceholders = String.join(", ", Collections.nCopies(visitsToStore.size(), valuePlaceholder));

        String sql = "INSERT INTO processed_visits (user_id, place_id, start_time, end_time, duration_seconds)\n" +
                "VALUES " + valuesPlaceholders + " RETURNING id;";

        List<Object> batchArgs = new ArrayList<>();
        for (ProcessedVisit visit : visitsToStore) {
            batchArgs.add(user.getId());
            batchArgs.add(visit.getPlace().getId());
            batchArgs.add(Timestamp.from(visit.getStartTime()));
            batchArgs.add(Timestamp.from(visit.getEndTime()));
            batchArgs.add(visit.getDurationSeconds());
        }

        List<Long> updateCounts = jdbcTemplate.query(sql, new ArgumentPreparedStatementSetter(batchArgs.toArray()), (resultSet, _) -> resultSet.getLong("id"));
        updateCounts.stream().map(this::findById).filter(Optional::isPresent).map(Optional::get).forEach(result::add);
        return result;
    }

    @SuppressWarnings("SqlWithoutWhere")
    public void deleteAll() {
        String sql = "DELETE FROM processed_visits";
        jdbcTemplate.update(sql);
    }

    public void deleteAllForUser(User user) {
        jdbcTemplate.update("DELETE FROM processed_visits WHERE user_id = ?", user.getId());
    }

    public List<LocalDate> getAffectedDays(List<SignificantPlace> places) {
        if (places.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> placeIds = places.stream()
                .map(SignificantPlace::getId)
                .toList();
        
        String placeholders = String.join(",", placeIds.stream().map(id -> "?").toList());
        String sql = """
                SELECT DISTINCT DATE(pv.start_time) AS affected_day
                FROM processed_visits pv
                WHERE pv.place_id IN (%s)
                UNION
                SELECT DISTINCT DATE(pv.end_time) AS affected_day
                FROM processed_visits pv
                WHERE pv.place_id IN (%s)
                ORDER BY affected_day;
                """.formatted(placeholders, placeholders);
        
        List<Object> params = new ArrayList<>();
        params.addAll(placeIds);
        params.addAll(placeIds);
        
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getDate("affected_day").toLocalDate(), params.toArray());
    }

    public void deleteFor(User user, List<SignificantPlace> placesToRemove) {
        Long[] idList = placesToRemove.stream().map(SignificantPlace::getId).toList().toArray(Long[]::new);
        this.jdbcTemplate.update("DELETE FROM processed_visits WHERE user_id = ? AND place_id = ANY(?)", user.getId(), idList);
    }
}
