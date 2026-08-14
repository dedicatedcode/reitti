package com.dedicatedcode.reitti.service.h3;

import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.repository.PointReaderWriter;
import com.dedicatedcode.reitti.service.JobContext;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

@DisallowConcurrentExecution
public class H3RecalculationJob implements Job {
    private static final Logger log = LoggerFactory.getLogger(H3RecalculationJob.class);

    private static final int BATCH_SIZE = 10_000;
    private static final int H3_RESOLUTION = 12;
    private static final int THREAD_COUNT = 4;

    private static final String DROP_POINTS_H3_INDEX = "DROP INDEX CONCURRENTLY IF EXISTS idx_points_h3_time";
    private static final String DROP_SOURCE_H3_INDEX = "DROP INDEX CONCURRENTLY IF EXISTS idx_source_points_h3_cell";
    private static final String CREATE_POINTS_H3_INDEX = "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_points_h3_time ON raw_location_points (h3_cell, timestamp) WHERE h3_cell IS NOT NULL";
    private static final String CREATE_SOURCE_H3_INDEX = "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_source_points_h3_cell ON raw_source_points (h3_cell) WHERE h3_cell IS NOT NULL";

    private static final String DROP_USER_TIME_SYNTHETIC_INDEX = "DROP INDEX CONCURRENTLY IF EXISTS idx_raw_location_points_user_time_synthetic";
    private static final String DROP_COVERING_USER_TIME_INDEX = "DROP INDEX CONCURRENTLY IF EXISTS idx_covering_user_time";
    private static final String DROP_ACTIVE_USERS_INDEX = "DROP INDEX CONCURRENTLY IF EXISTS idx_rlp_active_users";
    private static final String CREATE_USER_TIME_SYNTHETIC_INDEX = "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_raw_location_points_user_time_synthetic ON raw_location_points (user_id, timestamp, synthetic)";
    private static final String CREATE_COVERING_USER_TIME_INDEX = "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_covering_user_time ON raw_location_points (user_id, timestamp) INCLUDE (geom)";
    private static final String CREATE_ACTIVE_USERS_INDEX = "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_rlp_active_users ON raw_location_points (user_id, timestamp) WHERE processed = false";

    private final H3SpatialCoverageService spatialCoverageService;
    private final PointReaderWriter pointReaderWriter;
    private final JdbcTemplate jdbcTemplate;
    private final JobMetadataRepository jobMetadataRepository;

    public H3RecalculationJob(H3SpatialCoverageService spatialCoverageService,
                              PointReaderWriter pointReaderWriter,
                              JdbcTemplate jdbcTemplate,
                              JobMetadataRepository jobMetadataRepository) {
        this.spatialCoverageService = spatialCoverageService;
        this.pointReaderWriter = pointReaderWriter;
        this.jdbcTemplate = jdbcTemplate;
        this.jobMetadataRepository = jobMetadataRepository;
    }

    @SuppressWarnings("DataFlowIssue")
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.debug("Executing H3RecalculationJob");
        TaskData data = (TaskData) context.getMergedJobDataMap().get("data");
        long missingSourcePoints = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM raw_source_points WHERE h3_cell IS NULL", Long.class);
        long missingRawLocationPoints = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM raw_location_points WHERE h3_cell IS NULL AND source_point_id IS NULL", Long.class);
        long missingPointCount = missingSourcePoints + missingRawLocationPoints;
        if (missingPointCount == 0) {
            log.debug("H3 Data fully built, nothing to do");
        } else {
            jobMetadataRepository.updateProgress(data.getJobId(), 0, missingPointCount, "Recalculating H3 cells");
            long start = System.currentTimeMillis();
            log.info("Need to recalculate h3 cells for {} missing data points ({} source, {} device)", missingPointCount, missingSourcePoints, missingRawLocationPoints);

            log.info("Dropping H3 and non-unique secondary indexes for faster bulk update...");
            jdbcTemplate.execute(DROP_POINTS_H3_INDEX);
            jdbcTemplate.execute(DROP_SOURCE_H3_INDEX);
            jdbcTemplate.execute(DROP_USER_TIME_SYNTHETIC_INDEX);
            jdbcTemplate.execute(DROP_COVERING_USER_TIME_INDEX);
            jdbcTemplate.execute(DROP_ACTIVE_USERS_INDEX);

            try {
                long sourceStart = System.currentTimeMillis();
                recalculateSourcePoints(data, missingSourcePoints);
                log.info("raw_source_points H3 recalculation done in {}ms", System.currentTimeMillis() - sourceStart);

                long deviceStart = System.currentTimeMillis();
                recalculateLocationPoints(data, missingRawLocationPoints);
                log.info("raw_location_points H3 recalculation done in {}ms", System.currentTimeMillis() - deviceStart);
                log.info("Recalculation of {} H3 cells finished in {}ms, scheduling area stats updates now", missingPointCount, System.currentTimeMillis() - start);
            } finally {
                log.info("Rebuilding dropped indexes concurrently...");
                recreateIndex(CREATE_POINTS_H3_INDEX);
                recreateIndex(CREATE_SOURCE_H3_INDEX);
                recreateIndex(CREATE_USER_TIME_SYNTHETIC_INDEX);
                recreateIndex(CREATE_COVERING_USER_TIME_INDEX);
                recreateIndex(CREATE_ACTIVE_USERS_INDEX);
                log.info("Indexes rebuilt.");
            }
        }
    }

    private void recreateIndex(String ddl) {
        try {
            jdbcTemplate.execute(ddl);
        } catch (Exception e) {
            log.error("Failed to rebuild index with DDL: {}", ddl, e);
        }
    }

    private static String formatEta(long ms) {
        if (ms == 0) return "calculating...";
        if (ms < 1000) return "<1s";
        long seconds = ms / 1000;
        if (seconds < 60) return seconds + "s";
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    private void recalculateSourcePoints(TaskData data, long total) {
        String selectSql = "SELECT id, ST_AsText(geom) AS geom FROM raw_source_points WHERE h3_cell IS NULL AND id > ? ORDER BY id LIMIT ?";
        String updateSql = "UPDATE raw_source_points SET h3_cell = ? WHERE id = ?";
        AtomicLong current = new AtomicLong();
        AtomicLong firstBatchMs = new AtomicLong();
        long lastId = 0L;
        while (true) {
            List<PointRow> page = jdbcTemplate.query(selectSql, (rs, i) ->
                    new PointRow(rs.getLong("id"), pointReaderWriter.read(rs.getString("geom"))), lastId, BATCH_SIZE);
            if (page.isEmpty()) {
                break;
            }
            List<Object[]> batchBuffer = new ArrayList<>(page.size());
            List<Long> processedPoints = new ArrayList<>(page.size());
            for (PointRow row : page) {
                Long h3Cell = spatialCoverageService.getLevelCellForPoint(row.geom().latitude(), row.geom().longitude(), H3_RESOLUTION);
                processedPoints.add(row.id());
                batchBuffer.add(new Object[]{h3Cell, row.id()});
            }
            lastId = page.getLast().id();
            writeBatchToSourceTable(current, firstBatchMs, updateSql, batchBuffer, processedPoints, data, total);
        }
    }

    private void recalculateLocationPoints(TaskData data, long total) throws JobExecutionException {
        String selectSql = "SELECT id, ST_AsText(geom) AS geom FROM raw_location_points WHERE h3_cell IS NULL AND source_point_id IS NULL AND id > ? AND id < ? ORDER BY id LIMIT ?";
        String updateSql = "UPDATE raw_location_points SET h3_cell = ? WHERE id = ?";
        Long minId = jdbcTemplate.queryForObject("SELECT MIN(id) FROM raw_location_points WHERE h3_cell IS NULL AND source_point_id IS NULL", Long.class);
        Long maxId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM raw_location_points WHERE h3_cell IS NULL AND source_point_id IS NULL", Long.class);
        if (minId == null || maxId == null) {
            return;
        }

        AtomicLong current = new AtomicLong();
        AtomicLong firstBatchMs = new AtomicLong();

        long range = maxId - minId + 1;
        long chunkSize = (range + THREAD_COUNT - 1) / THREAD_COUNT;


        try (ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT)) {
            List<Future<?>> futures = new ArrayList<>(THREAD_COUNT);
            for (int i = 0; i < THREAD_COUNT; i++) {
                long lo = minId + i * chunkSize;
                long hiExclusive = Math.min(lo + chunkSize, maxId + 1L);
                if (lo >= hiExclusive) {
                    break;
                }
                long startId = lo - 1;
                futures.add(executor.submit(() -> processLocationRange(selectSql, updateSql, startId, hiExclusive, current, firstBatchMs, data, total)));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JobExecutionException("Location recalculation interrupted", e);
        } catch (ExecutionException e) {
            throw new JobExecutionException("Location recalculation failed", e);
        }
    }

    private void processLocationRange(String selectSql, String updateSql, long lastId, long hiExclusive, AtomicLong current, AtomicLong firstBatchMs, TaskData data, long total) {
        while (true) {
            List<PointRow> page = jdbcTemplate.query(selectSql, (rs, i) ->
                    new PointRow(rs.getLong("id"), pointReaderWriter.read(rs.getString("geom"))), lastId, hiExclusive, BATCH_SIZE);
            if (page.isEmpty()) {
                break;
            }
            List<Object[]> batchBuffer = new ArrayList<>(page.size());
            for (PointRow row : page) {
                Long h3Cell = spatialCoverageService.getLevelCellForPoint(row.geom().latitude(), row.geom().longitude(), H3_RESOLUTION);
                batchBuffer.add(new Object[]{h3Cell, row.id()});
            }
            lastId = page.getLast().id();
            writeBatchToLocationPoints(current, firstBatchMs, updateSql, batchBuffer, data, total);
        }
    }

    private record PointRow(long id, GeoPoint geom) {}

    private void writeBatchToLocationPoints(AtomicLong current, AtomicLong firstBatchMs, String updateLocationPointSql, List<Object[]> batchBuffer, TaskData data, long total) {
        int batchSize = batchBuffer.size();
        long batchStart = System.currentTimeMillis();
        this.jdbcTemplate.batchUpdate(updateLocationPointSql, batchBuffer, batchBuffer.size(), (ps, argument) -> {
            ps.setLong(1, (Long) argument[0]);
            ps.setLong(2, (Long) argument[1]);
        });
        current.addAndGet(batchSize);
        batchBuffer.clear();

        if (firstBatchMs.get() == 0) {
            firstBatchMs.set(System.currentTimeMillis() - batchStart);
        }
        jobMetadataRepository.updateProgress(data.getJobId(), current.get(), total, "Recalculating H3 cells");
        long remaining = total - current.get();
        long eta = firstBatchMs.get() > 0 ? ((remaining / batchSize) * firstBatchMs.get()) / THREAD_COUNT : 0;
        log.info("Recalculating Device H3 Cells Progress: {}/{}  ETA: {}", current.get(), total, formatEta(eta));
    }

    private void writeBatchToSourceTable(AtomicLong current, AtomicLong firstBatchMs, String updateSourcePointSql, List<Object[]> batchBuffer, List<Long> processedPoints, TaskData data, long total) {
        int batchSize = batchBuffer.size();
        long batchStart = System.currentTimeMillis();
        this.jdbcTemplate.batchUpdate(updateSourcePointSql, batchBuffer, batchBuffer.size(), (ps, argument) -> {
            ps.setLong(1, (Long) argument[0]);
            ps.setLong(2, (Long) argument[1]);
        });
        this.spatialCoverageService.postSourceRecalculation(processedPoints);
        current.addAndGet(batchSize);
        batchBuffer.clear();
        processedPoints.clear();

        if (firstBatchMs.get() == 0) {
            firstBatchMs.set(System.currentTimeMillis() - batchStart);
        }
        jobMetadataRepository.updateProgress(data.getJobId(), current.get(), total, "Recalculating H3 cells");
        long remaining = total - current.get();
        long eta = firstBatchMs.get() > 0 ? (remaining / batchSize) * firstBatchMs.get() : 0;
        log.info("Recalculating Source H3 Cells Progress: {}/{}  ETA: {}", current.get(), total, formatEta(eta));
    }

    public static class TaskData extends JobContext<TaskData> {
        public TaskData() {}

        private TaskData(UUID jobId, UUID parentJobId) {
            super(jobId, parentJobId);
        }

        @Override
        public TaskData withJobId(UUID jobId) {
            return new TaskData(jobId, parentJobId);
        }

        @Override
        public TaskData withParentJobId(UUID parentJobId) {
            return new TaskData(jobId, parentJobId);
        }
    }
}
