package com.dedicatedcode.reitti.service.h3;

import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.repository.PointReaderWriter;
import com.dedicatedcode.reitti.service.JobContext;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@DisallowConcurrentExecution
public class H3CellUpdateJob implements Job {
    private static final Logger log = LoggerFactory.getLogger(H3CellUpdateJob.class);

    private final JdbcTemplate jdbcTemplate;
    private final RocksDBH3Service rocksDbService;
    private final PointReaderWriter pointReaderWriter;

    public H3CellUpdateJob(JdbcTemplate jdbcTemplate, RocksDBH3Service rocksDbService, PointReaderWriter pointReaderWriter) {
        this.jdbcTemplate = jdbcTemplate;
        this.rocksDbService = rocksDbService;
        this.pointReaderWriter = pointReaderWriter;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        TaskData data = (TaskData) context.getMergedJobDataMap().get("data");

        log.debug("Updating H3 Spatial Statistics for {} new promoted ids", data.newPromotedIds.size());

        if (data.newPromotedIds.isEmpty()) {
            return;
        }

        // Create placeholders for IN clause
        String placeholders = String.join(",", Collections.nCopies(data.newPromotedIds.size(), "?"));
        String sql = "SELECT user_id, device_id, id, ST_AsText(geom) as geom_wkt, h3_cell, status " +
                     "FROM raw_location_points WHERE id IN (" + placeholders + ")";

        List<PointData> points = jdbcTemplate.query(sql, 
            (rs, rowNum) -> {
                long userId = rs.getLong("user_id");
                Long deviceId = rs.getObject("device_id", Long.class);
                long id = rs.getLong("id");
                String geomWkt = rs.getString("geom_wkt");
                long h3Cell = rs.getLong("h3_cell");
                int status = rs.getInt("status");
                
                GeoPoint geoPoint = pointReaderWriter.read(geomWkt);
                return new PointData(userId, deviceId, id, geoPoint.latitude(), geoPoint.longitude(), h3Cell, status);
            },
            data.newPromotedIds.toArray()
        );

        for (PointData point : points) {
            log.debug("Processing point: userId={}, deviceId={}, id={}, lat={}, lng={}, h3Cell={}, status={}", 
                     point.userId, point.deviceid, point.id, point.lat, point.lng, point.h3Cell, point.status);
            // TODO: Add your H3 processing logic here
        }
    }

    private record PointData(long userId, Long deviceid, long id, double lat, double lng, long h3Cell, int status) {}

    public static class TaskData extends JobContext<TaskData> {
        private final List<Long> newPromotedIds;
        public TaskData(List<Long> newPromotedIds) {
            this.newPromotedIds = newPromotedIds;
        }

        private TaskData(UUID jobId, UUID parentJobId, List<Long> newPromotedIds) {
            super(jobId, parentJobId);
            this.newPromotedIds = newPromotedIds;
        }

        public static TaskData forPromotion(List<Long> newPromotedIds) {
            return new TaskData(newPromotedIds);
        }

        @Override
        public TaskData withJobId(UUID jobId) {
            return new TaskData(jobId, parentJobId, newPromotedIds);
        }

        @Override
        public TaskData withParentJobId(UUID parentJobId) {
            return new TaskData(jobId, parentJobId, newPromotedIds);
        }
    }
}
