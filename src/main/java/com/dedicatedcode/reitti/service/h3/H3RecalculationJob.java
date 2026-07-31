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

    private static final int BATCH_SIZE = 10_000;
    private static final int H3_RESOLUTION = 12;

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
            AtomicLong current = new AtomicLong();
            jobMetadataRepository.updateProgress(data.getJobId(), 0, missingPointCount, "Recalculating H3 cells");
            long start = System.currentTimeMillis();
            log.info("Need to recalculate h3 cells for {} missing data points", missingPointCount);
            String selectSql = "SELECT id, ST_AsText(geom) AS geom FROM raw_source_points WHERE h3_cell IS NULL";

            String updateLocationPointSql = "UPDATE raw_location_points SET h3_cell = ? WHERE id = ?";
            String updateSourcePointSql = "UPDATE raw_source_points SET h3_cell = ? WHERE id = ?";

            List<Object[]> batchBuffer = new ArrayList<>(BATCH_SIZE);
            List<Long> processedPoints = new ArrayList<>(BATCH_SIZE);

            AtomicLong missingPoints = new AtomicLong(missingSourcePoints);
            jdbcTemplate.query(selectSql, rs -> {
                long id = rs.getLong("id");
                GeoPoint geom = pointReaderWriter.read(rs.getString("geom"));

                Long h3Cell = spatialCoverageService.getLevelCellForPoint(geom.latitude(), geom.longitude(), H3_RESOLUTION);
                processedPoints.add(id);
                batchBuffer.add(new Object[]{h3Cell, id});

                if (batchBuffer.size() >= BATCH_SIZE) {
                    writeBatchToSourceTable(current, updateSourcePointSql, batchBuffer, processedPoints, data, missingPoints);
                }
            });

            if (!batchBuffer.isEmpty()) {
                writeBatchToSourceTable(current, updateSourcePointSql, batchBuffer, processedPoints, data, missingPoints);
            }

            String selectMissedSourcePointSql = "SELECT id, ST_AsText(geom) AS geom FROM raw_location_points WHERE h3_cell IS NULL AND source_point_id IS NULL";
            List<Long> processedSourcePointIds = new ArrayList<>(BATCH_SIZE); // New list for source IDs
            jdbcTemplate.query(selectMissedSourcePointSql, rs -> {
                long id = rs.getLong("id");
                GeoPoint geom = pointReaderWriter.read(rs.getString("geom"));

                Long h3Cell = spatialCoverageService.getLevelCellForPoint(geom.latitude(), geom.longitude(), H3_RESOLUTION);
                batchBuffer.add(new Object[]{h3Cell, id});
                processedSourcePointIds.add(id); // Track the source ID
                if (batchBuffer.size() >= BATCH_SIZE) {
                    writeBatchToLocationPoints(current, updateLocationPointSql, batchBuffer, processedSourcePointIds, data, missingPoints);
                }
            });
            if (!batchBuffer.isEmpty()) {
                writeBatchToLocationPoints(current, updateLocationPointSql, batchBuffer, processedSourcePointIds, data, missingPoints);
            }
            log.info("Recalculation of {} H3 cells finished in {} ms, scheduling area stats updates now", missingPoints.get(), System.currentTimeMillis() - start);
        }
    }

    private void writeBatchToLocationPoints(AtomicLong current, String updateLocationPointSql, List<Object[]> batchBuffer, List<Long> processedSourcePointIds, TaskData data, AtomicLong missingPoints) {
        current.addAndGet(BATCH_SIZE);
        this.jdbcTemplate.batchUpdate(updateLocationPointSql, batchBuffer, batchBuffer.size(), (ps, argument) -> {
            ps.setLong(1, (Long) argument[0]); // h3_cell
            ps.setLong(2, (Long) argument[1]); // id
        });
        batchBuffer.clear();
        spatialCoverageService.postRecalculation(processedSourcePointIds);
        processedSourcePointIds.clear();
        jobMetadataRepository.updateProgress(data.getJobId(), current.get(), missingPoints.get(), "Recalculating H3 cells");
        log.info("Recalculating missing Device H3 Cells Progress: {}/{}", current.get(), missingPoints.get());
    }

    private void writeBatchToSourceTable(AtomicLong current, String updateSourcePointSql, List<Object[]> batchBuffer, List<Long> processedPoints, TaskData data, AtomicLong missingPoints) {
        current.addAndGet(BATCH_SIZE);
        this.jdbcTemplate.batchUpdate(updateSourcePointSql, batchBuffer, batchBuffer.size(), (ps, argument) -> {
            ps.setLong(1, (Long) argument[0]);
            ps.setLong(2, (Long) argument[1]);
        });
        this.spatialCoverageService.postSourceRecalculation(processedPoints);
        batchBuffer.clear();
        processedPoints.clear();
        jobMetadataRepository.updateProgress(data.getJobId(), current.get(), missingPoints.get(), "Recalculating H3 cells");
        log.info("Recalculating H3 Cells Progress: {}/{}", current.get(), missingPoints.get());
    }

    private void flushBatch(String updateSourcePointSql, String updateLocationPointSql, List<Object[]> batchBuffer) {


        this.jdbcTemplate.batchUpdate(updateLocationPointSql, batchBuffer, batchBuffer.size(), (ps, argument) -> {
            ps.setLong(1, (Long) argument[0]); // h3_cell
            ps.setLong(2, (Long) argument[1]); // id
        });
        batchBuffer.clear();
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
