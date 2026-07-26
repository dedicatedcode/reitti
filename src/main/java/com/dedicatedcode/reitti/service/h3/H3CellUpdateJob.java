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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
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

        // Process in batches to avoid memory issues and database timeouts
        final int batchSize = 1000;
        List<Long> ids = data.newPromotedIds;
        
        for (int i = 0; i < ids.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, ids.size());
            List<Long> batch = ids.subList(i, endIndex);
            
            log.debug("Processing batch {}/{}: {} points", 
                     (i / batchSize) + 1, 
                     (ids.size() + batchSize - 1) / batchSize, 
                     batch.size());
            switch (data.changeType) {
                case DELETION -> {
                }
                case PROMOTION -> processBatchForPromotion(batch);
            }
        }
    }

    private void processBatchForPromotion(List<Long> batchIds) {
        String placeholders = String.join(",", Collections.nCopies(batchIds.size(), "?"));
        String sql = "SELECT user_id, device_id, id, ST_AsText(geom) as geom_wkt, h3_cell, status, timestamp " +
                "FROM raw_source_points WHERE id IN (" + placeholders + ")";

        List<PointData> points = jdbcTemplate.query(sql,
                                                    (rs, rowNum) -> {
                                                        long userId = rs.getLong("user_id");
                                                        Long deviceId = rs.getObject("device_id", Long.class);
                                                        long id = rs.getLong("id");
                                                        String geomWkt = rs.getString("geom_wkt");
                                                        long h3Cell = rs.getLong("h3_cell");
                                                        int status = rs.getInt("status");
                                                        Instant timestamp = rs.getTimestamp("timestamp").toInstant();

                                                        GeoPoint geoPoint = pointReaderWriter.read(geomWkt);
                                                        return new PointData(userId, deviceId, id, geoPoint.latitude(), geoPoint.longitude(), h3Cell, status, timestamp);
                                                    },
                                                    batchIds.toArray()
        );

        for (PointData point : points) {
            log.debug("Processing point: userId={}, deviceId={}, id={}, lat={}, lng={}, h3Cell={}, status={}",
                      point.userId, point.deviceid, point.id, point.lat, point.lng, point.h3Cell, point.status);

            // Get all H3 cells for this point at different resolutions (hierarchical)
            Set<Long> h3Cells = rocksDbService.getCellsForPoint(point.lat, point.lng);

            // Process each H3 cell (at different resolutions)
            for (Long h3Cell : h3Cells) {
                // Check if this cell was already visited by this user
                boolean isNewCell = isNewCellForUser(point.userId, h3Cell);

                // Update or insert H3 cell stats
                String upsertCellSql = """
                INSERT INTO h3_cells_stats (user_id, device_id, h3_index, last_visited_at, point_count)
                VALUES (?, ?, ?, ?, 1)
                ON CONFLICT (user_id, device_id, h3_index) DO UPDATE SET
                    last_visited_at = GREATEST(h3_cells_stats.last_visited_at, ?),
                    point_count = h3_cells_stats.point_count + 1
                """;

                jdbcTemplate.update(upsertCellSql, point.userId, point.deviceid, h3Cell,
                                    Timestamp.from(point.timestamp), Timestamp.from(point.timestamp));

                // Only update area coverage if this is a new cell for the user
                if (isNewCell) {
                    updateAreaCoverageForCell(point, h3Cell);
                }
            }
        }
    }

    private boolean isNewCellForUser(long userId, long h3Cell) {
        String checkSql = "SELECT COUNT(*) FROM h3_cells_stats WHERE user_id = ? AND h3_index = ?";
        Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, userId, h3Cell);
        return count == null || count == 0;
    }

    private void updateAreaCoverageForCell(PointData point, long h3Cell) {
        // Get OSM boundaries that contain this H3 cell
        List<RocksDBH3Service.CellWithBoundaries> cellsWithBoundaries =
                rocksDbService.getCellsWithBoundaries(point.lat, point.lng);

        for (RocksDBH3Service.CellWithBoundaries cellWithBoundary : cellsWithBoundaries) {
            if (cellWithBoundary.cellId() == h3Cell) {
                int resolution = cellWithBoundary.resolution();

                for (Long osmId : cellWithBoundary.osmIds()) {
                    // Get total cell count for this OSM area at this resolution from RocksDB
                    List<RocksDBH3Service.BoundaryInfo> boundaryInfos =
                            rocksDbService.lookup(point.lat, point.lng);

                    int totalCells = boundaryInfos.stream()
                            .filter(bi -> bi.osmId() == osmId && bi.resolution() == resolution)
                            .mapToInt(RocksDBH3Service.BoundaryInfo::totalCells)
                            .findFirst()
                            .orElse(0);

                    if (totalCells > 0) {
                        // Update area coverage stats - increment visited cells for this resolution
                        String upsertAreaSql = """
                        INSERT INTO h3_area_coverage_stats (user_id, device_id, osm_id, h3_resolution, visited_cell_count, total_cell_count)
                        VALUES (?, ?, ?, ?, 1, ?)
                        ON CONFLICT (user_id, device_id, osm_id, h3_resolution) DO UPDATE SET
                            visited_cell_count = h3_area_coverage_stats.visited_cell_count + 1,
                            total_cell_count = ?
                        """;

                        jdbcTemplate.update(upsertAreaSql,
                                            point.userId, point.deviceid, osmId, resolution,
                                            totalCells, totalCells);
                    }
                }
            }
        }
    }

    private record PointData(long userId, Long deviceid, long id, double lat, double lng, long h3Cell, int status,
                             Instant timestamp) {}

    public enum ChangeType {
        DELETION, PROMOTION

    }
    public static class TaskData extends JobContext<TaskData> {
        private final ChangeType changeType;
        private final List<Long> newPromotedIds;

        public TaskData(ChangeType changeType, List<Long> newPromotedIds) {
            this.changeType = changeType;
            this.newPromotedIds = newPromotedIds;
        }

        private TaskData(UUID jobId, UUID parentJobId, ChangeType changeType, List<Long> newPromotedIds) {
            super(jobId, parentJobId);
            this.changeType = changeType;
            this.newPromotedIds = newPromotedIds;
        }

        public static TaskData forPromotion(List<Long> newPromotedIds) {
            return new TaskData(ChangeType.PROMOTION, newPromotedIds);
        }

        public static TaskData forDeletion(List<Long> deletedPointIds) {
            return new TaskData(ChangeType.DELETION, deletedPointIds);
        }

        @Override
        public TaskData withJobId(UUID jobId) {
            return new TaskData(jobId, parentJobId, changeType, newPromotedIds);
        }

        @Override
        public TaskData withParentJobId(UUID parentJobId) {
            return new TaskData(jobId, parentJobId, changeType, newPromotedIds);
        }
    }
}
