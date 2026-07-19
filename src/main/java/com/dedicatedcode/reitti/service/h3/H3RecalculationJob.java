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

    private static final int BATCH_SIZE = 2000;

    private final RocksDBH3Service rocksDBH3Service;
    private final PointReaderWriter pointReaderWriter;
    private final JdbcTemplate jdbcTemplate;
    private final JobMetadataRepository jobMetadataRepository;

    public H3RecalculationJob(RocksDBH3Service rocksDBH3Service,
                              PointReaderWriter pointReaderWriter,
                              JdbcTemplate jdbcTemplate,
                              JobMetadataRepository jobMetadataRepository) {
        this.rocksDBH3Service = rocksDBH3Service;
        this.pointReaderWriter = pointReaderWriter;
        this.jdbcTemplate = jdbcTemplate;
        this.jobMetadataRepository = jobMetadataRepository;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.debug("Executing H3RecalculationJob");
        TaskData data = (TaskData) context.getMergedJobDataMap().get("data");
        Long missingDataPoints = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM raw_location_points WHERE source_point_id IS NOT NULL AND h3_res9 IS NULL", Long.class);

        if (missingDataPoints == null || missingDataPoints == 0) {
            log.debug("H3 Data fully built, nothing to do");
        } else {
            AtomicLong current = new AtomicLong();
            jobMetadataRepository.updateProgress(data.getJobId(), 0, missingDataPoints, "Recalculating H3 cells");
            long start = System.currentTimeMillis();
            log.info("Need to recalculate h3 cells for {} missing data points", missingDataPoints);
            String selectSql = "SELECT source_point_id, ST_AsText(geom) as geom FROM raw_location_points WHERE source_point_id IS NOT NULL AND h3_res9 IS NULL";
            String updateLocationPointSql = "UPDATE raw_location_points SET h3_res9 = ? WHERE source_point_id = ?";
            String updateSourcePointSql = "UPDATE raw_source_points SET h3_res9 = ? WHERE id = ?";

            List<Object[]> batchBuffer = new ArrayList<>(BATCH_SIZE);

            jdbcTemplate.query(selectSql, rs -> {
                long sourceId = rs.getLong("source_point_id");
                GeoPoint geom = pointReaderWriter.read(rs.getString("geom"));

                long h3Res9Cell = rocksDBH3Service.getLevel9CellForPoint(geom.latitude(), geom.longitude());

                batchBuffer.add(new Object[]{h3Res9Cell, sourceId});

                if (batchBuffer.size() >= BATCH_SIZE) {
                    current.addAndGet(BATCH_SIZE);
                    flushBatch(updateSourcePointSql, updateLocationPointSql, batchBuffer, current);
                    jobMetadataRepository.updateProgress(data.getJobId(), current.get(), missingDataPoints, "Recalculating H3 cells");
                }
            });

            if (!batchBuffer.isEmpty()) {
                current.addAndGet(batchBuffer.size());
                flushBatch(updateSourcePointSql, updateLocationPointSql, batchBuffer, current);
                jobMetadataRepository.updateProgress(data.getJobId(), current.get(), missingDataPoints, "Recalculating H3 cells");
            }
            log.info("Recalculation of {} H3 cells finished in {} ms", missingDataPoints, System.currentTimeMillis() - start);
        }
    }

    private void flushBatch(String updateSourcePointSql, String updateLocationPointSql, List<Object[]> batchBuffer, AtomicLong current) {
        this.jdbcTemplate.batchUpdate(updateSourcePointSql, batchBuffer);
        this.jdbcTemplate.batchUpdate(updateLocationPointSql, batchBuffer);
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
