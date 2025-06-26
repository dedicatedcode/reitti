package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.User;
import com.dedicatedcode.reitti.model.Visit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class VisitJdbcService {
    private final JdbcTemplate jdbcTemplate;

    public VisitJdbcService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Visit> VISIT_ROW_MAPPER = (rs, _) -> new Visit(
            rs.getLong("id"),
            rs.getDouble("longitude"),
            rs.getDouble("latitude"),
            rs.getTimestamp("start_time").toInstant(),
            rs.getTimestamp("end_time").toInstant(),
            rs.getLong("duration_seconds"),
            rs.getBoolean("processed"),
            rs.getLong("version")
    );

    public List<Visit> findByUser(User user) {
        String sql = "SELECT v.* " +
                "FROM visits v " +
                "WHERE v.user_id = ? ORDER BY start_time";
        return jdbcTemplate.query(sql, VISIT_ROW_MAPPER, user.getId());
    }

    public List<Visit> findByUserAndStartTimeAndEndTime(User user, Instant startTime, Instant endTime) {
        String sql = "SELECT v.* " +
                "FROM visits v " +
                "WHERE v.user_id = ? AND v.start_time = ? AND v.end_time = ?";
        return jdbcTemplate.query(sql, VISIT_ROW_MAPPER, user.getId(),
                Timestamp.from(startTime), Timestamp.from(endTime));
    }

    public Visit create(User user, Visit visit) {
        String sql = "INSERT INTO visits (user_id, longitude, latitude, start_time, end_time, duration_seconds, processed, version) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?,?) RETURNING id";
        Long id = jdbcTemplate.queryForObject(sql, Long.class,
                user.getId(),
                visit.getLongitude(),
                visit.getLatitude(),
                Timestamp.from(visit.getStartTime()),
                Timestamp.from(visit.getEndTime()),
                visit.getDurationSeconds(),
                visit.isProcessed(),
                visit.getVersion()
        );
        return visit.withId(id);
    }

    public Visit update(Visit visit) throws OptimisticLockException {
        String sql = "UPDATE visits SET longitude = ?, latitude = ?, start_time = ?, end_time = ?, duration_seconds = ?, processed = ?, version = version + 1 WHERE id = ? AND version = ?";
        int rowsUpdated = jdbcTemplate.update(sql,
                visit.getLongitude(),
                visit.getLatitude(),
                Timestamp.from(visit.getStartTime()),
                Timestamp.from(visit.getEndTime()),
                visit.getDurationSeconds(),
                visit.isProcessed(),
                visit.getId(),
                visit.getVersion()
        );
        
        if (rowsUpdated == 0) {
            throw new OptimisticLockException("Visit with id " + visit.getId() + " was modified by another transaction or does not exist");
        }
        
        return visit.withVersion(visit.getVersion() + 1);
    }

    public Optional<Visit> findById(Long id) {
        String sql = "SELECT v.* " +
                "FROM visits v " +
                "WHERE v.id = ?";
        List<Visit> results = jdbcTemplate.query(sql, VISIT_ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public List<Visit> findAllByIds(List<Long> visitIds) {
        if (visitIds == null || visitIds.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(",", visitIds.stream().map(_ -> "?").toList());
        String sql = "SELECT v.* FROM visits v WHERE v.id IN (" + placeholders + ")";
        
        return jdbcTemplate.query(sql, VISIT_ROW_MAPPER, visitIds.toArray());
    }

    @SuppressWarnings("SqlWithoutWhere")
    public void deleteAll() {
        String sql = "DELETE FROM visits";
        jdbcTemplate.update(sql);
    }

    public void deleteAllForUser(User user) {
        String sql = "DELETE FROM visits WHERE user_id = ?";
        jdbcTemplate.update(sql, user.getId());
    }

    public List<Visit> findByUserAndTimeAfterAndStartTimeBefore(User user, Instant windowStart, Instant windowEnd) {
        String sql = "SELECT v.* " +
                "FROM visits v " +
                "WHERE v.user_id = ? AND v.end_time >= ? AND v.start_time <= ? " +
                "ORDER BY v.start_time";
        return jdbcTemplate.query(sql, VISIT_ROW_MAPPER, user.getId(),
                Timestamp.from(windowStart), Timestamp.from(windowEnd));
    }

    public List<Visit> bulkInsert(User user, List<Visit> visitsToInsert) {
        if (visitsToInsert.isEmpty()) {
            return new ArrayList<>();
        }

        List<Visit> createdVisits = new ArrayList<>();
        String sql = """
                INSERT INTO visits (user_id, latitude, longitude, start_time, end_time, duration_seconds, processed, version)
                VALUES (?, ?, ?, ?, ?, ?, false, 1) ON CONFLICT DO NOTHING;
                """;

        List<Object[]> batchArgs = visitsToInsert.stream()
                .map(visit -> new Object[]{
                        user.getId(),
                        visit.getLatitude(),
                        visit.getLongitude(),
                        Timestamp.from(visit.getStartTime()),
                        Timestamp.from(visit.getEndTime()),
                        visit.getDurationSeconds()
                })
                .collect(Collectors.toList());

        int[] updateCounts = jdbcTemplate.batchUpdate(sql, batchArgs);
        for (int i = 0; i < updateCounts.length; i++) {
            int updateCount = updateCounts[i];
            if (updateCount > 0) {
                createdVisits.addAll(this.findByUserAndStartTimeAndEndTime(user, visitsToInsert.get(i).getStartTime(), visitsToInsert.get(i).getEndTime()));
            }
        }
        return createdVisits;
    }

    public void delete(List<Visit> affectedVisits) throws OptimisticLockException {
        if (affectedVisits == null || affectedVisits.isEmpty()) {
            return;
        }
        
        // Check versions for all visits before deleting any
        for (Visit visit : affectedVisits) {
            String checkSql = "SELECT version FROM visits WHERE id = ?";
            List<Long> versions = jdbcTemplate.queryForList(checkSql, Long.class, visit.getId());
            
            if (versions.isEmpty()) {
                throw new OptimisticLockException("Visit with id " + visit.getId() + " does not exist");
            }
            
            if (!versions.getFirst().equals(visit.getVersion())) {
                throw new OptimisticLockException("Visit with id " + visit.getId() + " was modified by another transaction");
            }
        }

        String placeholders = String.join(",", affectedVisits.stream().map(_ -> "?").toList());
        String sql = "DELETE FROM visits WHERE id IN (" + placeholders + ")";
        
        Object[] ids = affectedVisits.stream().map(Visit::getId).toArray();
        jdbcTemplate.update(sql, ids);
    }
}
