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
import java.util.concurrent.atomic.AtomicLong;

@DisallowConcurrentExecution
public class H3RecalculationJob implements Job {
    private static final Logger log = LoggerFactory.getLogger(H3RecalculationJob.class);

    private static final int BATCH_SIZE = 50_000;
    private static final int H3_RESOLUTION = 12;

    private static final String DROP_POINTS_H3_INDEX = "DROP INDEX CONCURRENTLY IF EXISTS idx_points_h3_time";
    private static final String DROP_SOURCE_H3_INDEX = "DROP INDEX CONCURRENTLY IF EXISTS idx_source_points_h3_cell";
    private static final String CREATE_POINTS_H3_INDEX = "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_points_h3_time ON raw_location_points (h3_cell, timestamp) WHERE h3_cell IS NOT NULL";
    private static final String CREATE_SOURCE_H3_INDEX = "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_source_points_h3_cell ON raw_source_points (h3_cell) WHERE h3_cell IS NOT NULL";

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

            log.info("Dropping H3 partial indexes for faster bulk update...");
            jdbcTemplate.execute(DROP_POINTS_H3_INDEX);
            jdbcTemplate.execute(DROP_SOURCE_H3_INDEX);

            try {
                String selectSql = "SELECT id, ST_AsText(geom) AS geom FROM raw_source_points WHERE h3_cell IS NULL";

                String updateLocationPointSql = "UPDATE raw_location_points SET h3_cell = ? WHERE id = ?";
                String updateSourcePointSql = "UPDATE raw_source_points SET h3_cell = ? WHERE id = ?";

                List<Object[]> batchBuffer = new ArrayList<>(BATCH_SIZE);
                List<Long> processedPoints = new ArrayList<>(BATCH_SIZE);

                long sourceStart = System.currentTimeMillis();
                AtomicLong current = new AtomicLong();
                AtomicLong firstBatchMs = new AtomicLong();
                jdbcTemplate.query(selectSql, rs -> {
                    long id = rs.getLong("id");
                    GeoPoint geom = pointReaderWriter.read(rs.getString("geom"));

                    Long h3Cell = spatialCoverageService.getLevelCellForPoint(geom.latitude(), geom.longitude(), H3_RESOLUTION);
                    processedPoints.add(id);
                    batchBuffer.add(new Object[]{h3Cell, id});

                    if (batchBuffer.size() >= BATCH_SIZE) {
                        writeBatchToSourceTable(current, firstBatchMs, updateSourcePointSql, batchBuffer, processedPoints, data, missingSourcePoints);
                    }
                });

                if (!batchBuffer.isEmpty()) {
                    writeBatchToSourceTable(current, firstBatchMs, updateSourcePointSql, batchBuffer, processedPoints, data, missingSourcePoints);
                }
                log.info("raw_source_points H3 recalculation done in {}ms", System.currentTimeMillis() - sourceStart);

                String selectMissedSourcePointSql = "SELECT id, ST_AsText(geom) AS geom FROM raw_location_points WHERE h3_cell IS NULL AND source_point_id IS NULL";
                AtomicLong deviceCurrent = new AtomicLong();
                AtomicLong deviceFirstBatchMs = new AtomicLong();
                long deviceStart = System.currentTimeMillis();
                jdbcTemplate.query(selectMissedSourcePointSql, rs -> {
                    long id = rs.getLong("id");
                    GeoPoint geom = pointReaderWriter.read(rs.getString("geom"));

                    Long h3Cell = spatialCoverageService.getLevelCellForPoint(geom.latitude(), geom.longitude(), H3_RESOLUTION);
                    batchBuffer.add(new Object[]{h3Cell, id});
                    if (batchBuffer.size() >= BATCH_SIZE) {
                        writeBatchToLocationPoints(deviceCurrent, deviceFirstBatchMs, updateLocationPointSql, batchBuffer, data, missingRawLocationPoints);
                    }
                });
                if (!batchBuffer.isEmpty()) {
                    writeBatchToLocationPoints(deviceCurrent, deviceFirstBatchMs, updateLocationPointSql, batchBuffer, data, missingRawLocationPoints);
                }
                log.info("raw_location_points H3 recalculation done in {}ms", System.currentTimeMillis() - deviceStart);
                log.info("Recalculation of {} H3 cells finished in {}ms, scheduling area stats updates now", missingPointCount, System.currentTimeMillis() - start);
            } finally {
                log.info("Rebuilding H3 partial indexes concurrently...");
                jdbcTemplate.execute(CREATE_POINTS_H3_INDEX);
                jdbcTemplate.execute(CREATE_SOURCE_H3_INDEX);
                log.info("H3 partial indexes rebuilt.");
            }
        }
    }

    private static String formatEta(long ms) {
        if (ms == 0) return "calculating...";
        if (ms < 1000) return "<1s";
        long seconds = ms / 1000;
        if (seconds < 60) return seconds + "s";
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }

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
        long eta = firstBatchMs.get() > 0 ? (remaining / batchSize) * firstBatchMs.get() : 0;
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
