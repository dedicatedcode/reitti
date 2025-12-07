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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class PreviewProcessedVisitJdbcService {

    private final JdbcTemplate jdbcTemplate;
    private final PreviewSignificantPlaceJdbcService significantPlaceJdbcService;

    public PreviewProcessedVisitJdbcService(JdbcTemplate jdbcTemplate, PreviewSignificantPlaceJdbcService significantPlaceJdbcService) {
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

    public Optional<ProcessedVisit> findById(Long id) {
        String sql = "SELECT pv.* " +
                "FROM preview_processed_visits pv " +
                "WHERE pv.id = ?";
        List<ProcessedVisit> results = jdbcTemplate.query(sql, PROCESSED_VISIT_ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<ProcessedVisit> findByUserAndTimeOverlap(User user, String previewId, Instant startTime, Instant endTime) {
        String sql = "SELECT pv.* " +
                "FROM preview_processed_visits pv " +
                "WHERE pv.user_id = ? AND pv.start_time <= ? AND pv.end_time >= ? AND preview_id = ? ORDER BY pv.start_time";
        return jdbcTemplate.query(sql, PROCESSED_VISIT_ROW_MAPPER, user.getId(),
                Timestamp.from(endTime), Timestamp.from(startTime), previewId);
    }
    public Optional<ProcessedVisit> findByUserAndId(User user, long id) {
        String sql = "SELECT pv.* " +
                "FROM preview_processed_visits pv " +
                "WHERE pv.user_id = ? AND pv.id = ? ORDER BY pv.start_time";
        List<ProcessedVisit> results = jdbcTemplate.query(sql, PROCESSED_VISIT_ROW_MAPPER, user.getId(), id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<ProcessedVisit> findByUserAndStartTimeBeforeEqualAndEndTimeAfterEqual(User user, String previewId, Instant endTime, Instant startTime) {
        String sql = "SELECT pv.* " +
                "FROM preview_processed_visits pv " +
                "WHERE pv.user_id = ? AND pv.start_time <= ? AND pv.end_time >= ? AND preview_id = ? ORDER BY start_time";
        return jdbcTemplate.query(sql, PROCESSED_VISIT_ROW_MAPPER, user.getId(),
                Timestamp.from(endTime), Timestamp.from(startTime), previewId);
    }

    public void deleteAll(List<ProcessedVisit> processedVisits) {
        if (processedVisits == null || processedVisits.isEmpty()) {
            return;
        }

        List<Long> ids = processedVisits.stream()
                .map(ProcessedVisit::getId)
                .toList();

        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        String sql = "DELETE FROM preview_processed_visits WHERE id IN (" + placeholders + ")";

        jdbcTemplate.update(sql, ids.toArray());
    }

    public Optional<ProcessedVisit> findByUserAndStartTimeAndEndTimeAndPlace(User user, String previewId, Instant startTime, Instant endTime, SignificantPlace place) {
        String sql = "SELECT pv.* " +
                "FROM preview_processed_visits pv " +
                "WHERE pv.user_id = ? AND pv.start_time = ? AND pv.end_time = ? AND pv.place_id = ? AND pv.preview_id = ? ORDER BY pv.start_time";
        List<ProcessedVisit> results = jdbcTemplate.query(sql, PROCESSED_VISIT_ROW_MAPPER,
                user.getId(),
                Timestamp.from(startTime),
                Timestamp.from(endTime),
                place.getId(),
                previewId);
        return Optional.ofNullable(results.isEmpty() ? null : results.getFirst());
    }

    public List<ProcessedVisit> bulkInsert(User user, String previewId, List<ProcessedVisit> visitsToStore) {
        if (visitsToStore.isEmpty()) {
            return new ArrayList<>();
        }

        List<ProcessedVisit> result = new ArrayList<>();

        // 1. Build the multi-row INSERT statement structure
        String valuePlaceholder = "(?, ?, ?, ?, ?, ?, now())";
        String valuesPlaceholders = String.join(", ", Collections.nCopies(visitsToStore.size(), valuePlaceholder));

        String sql = "INSERT INTO preview_processed_visits (user_id, place_id, start_time, end_time, duration_seconds, preview_id, preview_created_at)\n" +
                     "VALUES " + valuesPlaceholders + " RETURNING id;";

        List<Object> batchArgs = new ArrayList<>();
        for (ProcessedVisit visit : visitsToStore) {
            batchArgs.add(user.getId());
            batchArgs.add(visit.getPlace().getId());
            batchArgs.add(Timestamp.from(visit.getStartTime()));
            batchArgs.add(Timestamp.from(visit.getEndTime()));
            batchArgs.add(visit.getDurationSeconds());
            batchArgs.add(previewId);
        }

        List<Long> updateCounts = jdbcTemplate.query(sql, new ArgumentPreparedStatementSetter(batchArgs.toArray()),  (resultSet, _) -> resultSet.getLong("id"));
        updateCounts.stream().map(this::findById).filter(Optional::isPresent).map(Optional::get).forEach(result::add);
        return result;
    }
}
