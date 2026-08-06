package com.dedicatedcode.reitti.service.h3;

import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.repository.PointReaderWriter;
import com.dedicatedcode.reitti.service.JobContext;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.jobs.JobType;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.Serializable;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@DisallowConcurrentExecution
public class H3CellUpdateJob implements Job {
    private static final Logger log = LoggerFactory.getLogger(H3CellUpdateJob.class);

    private final JdbcTemplate jdbcTemplate;
    private final RocksDBH3Service rocksDbService;
    private final PointReaderWriter pointReaderWriter;
    private final JobSchedulingService jobSchedulingService;

    public H3CellUpdateJob(JdbcTemplate jdbcTemplate, RocksDBH3Service rocksDbService, PointReaderWriter pointReaderWriter, JobSchedulingService jobSchedulingService) {
        this.jdbcTemplate = jdbcTemplate;
        this.rocksDbService = rocksDbService;
        this.pointReaderWriter = pointReaderWriter;
        this.jobSchedulingService = jobSchedulingService;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        TaskData data = (TaskData) context.getMergedJobDataMap().get("data");

        if (!rocksDbService.isAvailable()) {
            log.debug("RocksDB is not available yet. Rescheduling job.");
            jobSchedulingService.scheduleTask(
                    context.getJobDetail(),
                    data,
                    Instant.now().plusSeconds(5),
                    JobSchedulingService.Metadata.builder()
                            .jobType(JobType.H3_CELL_UPDATE)
                            .friendlyName("Updating H3 Spatial Statistics (retry)")
                            .build()
            );
            return;
        }

        if (data.changeType == ChangeType.MOVEMENT) {
            log.debug("Processing movement for {} points", data.movedPoints.size());
            processMovement(data.movedPoints);
            return;
        }

        if (data.changeType == ChangeType.INCREMENT) {
            log.debug("Processing increment for {} cells", data.cellIncrements.size());
            processIncrement(data.cellIncrements);
            return;
        }

        log.debug("Updating H3 Spatial Statistics for {} new promoted ids", data.pointIds.size());

        if (data.pointIds.isEmpty()) {
            return;
        }

        // Process in batches to avoid memory issues and database timeouts
        final int batchSize = 1000;
        List<Long> ids = data.pointIds;

        for (int i = 0; i < ids.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, ids.size());
            List<Long> batch = ids.subList(i, endIndex);

            log.debug("Processing batch {}/{}: {} points",
                      (i / batchSize) + 1,
                      (ids.size() + batchSize - 1) / batchSize,
                      batch.size());
            switch (data.changeType) {
                case DELETION -> processBatchForDeletion(batch);
                case PROMOTION -> processBatchForPromotion(batch);
            }
        }
    }

    private void processMovement(List<MovedPoint> movedPoints) {
        if (movedPoints.isEmpty()) {
            return;
        }
        // 1. Delete old locations using stored res-12 cells
        List<PointData> oldPoints = loadPointDataForMoved(movedPoints, true);
        for (PointData point : oldPoints) {
            log.trace("Processing moved point (deletion): userId={}, deviceId={}, id={}, h3Cell={}",
                      point.userId, point.deviceid, point.id, point.h3Cell);

            Set<Long> parentCells = rocksDbService.getParentCells(point.h3Cell);
            for (Long parentCell : parentCells) {
                decrementCellAndCheckRemoval(point.userId, point.deviceid, parentCell);
            }
        }

        // 2. Promote new locations
        List<PointData> newPoints = loadPointDataForMoved(movedPoints, false);
        for (PointData point : newPoints) {
            promotePoint(point);
        }
    }

    private void processBatchForPromotion(List<Long> batchIds) {
        List<PointData> points = loadPointData(batchIds);
        for (PointData point : points) {
            promotePoint(point);
        }
    }

    private void promotePoint(PointData point) {
        log.trace("Processing point: userId={}, deviceId={}, id={}, lat={}, lng={}, h3Cell={}, status={}",
                  point.userId, point.deviceid, point.id, point.lat, point.lng, point.h3Cell, point.status);

        Set<Long> parentCells = rocksDbService.getParentCells(point.h3Cell);

        for (Long parentCell : parentCells) {
            boolean isNewCell = isNewCellForUser(point.userId, point.deviceid, parentCell);

            upsertCellStats(point.userId, point.deviceid, parentCell, point.timestamp);

            if (isNewCell) {
                int resolution = rocksDbService.getResolution(parentCell);
                Set<Long> osmIds = rocksDbService.getOsmIds(parentCell);
                for (Long osmId : osmIds) {
                    incrementAreaVisitedCells(point.userId, point.deviceid, osmId, resolution);
                }
            }
        }
    }

    private void upsertCellStats(long userId, Long deviceId, long h3Cell, Instant timestamp) {
        String sql = """
                INSERT INTO h3_cells_stats (user_id, device_id, h3_index,
                                            last_visited_at, point_count, first_visited_at)
                VALUES (?, ?, ?, ?, 1, ?)
                ON CONFLICT (user_id, device_id, h3_index) DO UPDATE SET
                    last_visited_at = GREATEST(h3_cells_stats.last_visited_at, ?),
                    point_count = h3_cells_stats.point_count + 1,
                    first_visited_at = LEAST(h3_cells_stats.first_visited_at, ?)
                """;
        jdbcTemplate.update(sql,
                            userId, deviceId, h3Cell,
                            Timestamp.from(timestamp),
                            Timestamp.from(timestamp),
                            Timestamp.from(timestamp),
                            Timestamp.from(timestamp));
    }

    private void incrementAreaVisitedCells(long userId, Long deviceId, long osmId, int resolution) {
        int totalCells = rocksDbService.getTotalCells(osmId, resolution);
        if (totalCells <= 0) return;

        String sql = """
                INSERT INTO h3_area_coverage_stats (user_id, device_id, osm_id, h3_resolution, visited_cell_count, total_cell_count)
                VALUES (?, ?, ?, ?, 1, ?)
                ON CONFLICT (user_id, device_id, osm_id, h3_resolution) DO UPDATE SET
                    visited_cell_count = h3_area_coverage_stats.visited_cell_count + 1,
                    total_cell_count = ?
                """;
        jdbcTemplate.update(sql, userId, deviceId, osmId, resolution, totalCells, totalCells);
    }

    private List<PointData> loadPointData(List<Long> batchIds) {
        String placeholders = String.join(",", Collections.nCopies(batchIds.size(), "?"));
        String sql = "SELECT user_id, device_id, id, ST_AsText(geom) as geom_wkt, h3_cell, status, timestamp " +
                "FROM raw_source_points WHERE id IN (" + placeholders + ")";

        return jdbcTemplate.query(sql,
                                  (rs, _) -> {
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
    }

    private List<PointData> loadPointDataForMoved(List<MovedPoint> movedPoints, boolean useOldCoords) {
        List<Long> ids = movedPoints.stream().map(MovedPoint::id).toList();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = "SELECT user_id, device_id, id, status, timestamp " +
                "FROM raw_source_points WHERE id IN (" + placeholders + ")";

        Map<Long, MovedPoint> movedMap = movedPoints.stream().collect(Collectors.toMap(MovedPoint::id, mp -> mp));

        return jdbcTemplate.query(sql,
                                  (rs, _) -> {
                                      long userId = rs.getLong("user_id");
                                      Long deviceId = rs.getObject("device_id", Long.class);
                                      long id = rs.getLong("id");
                                      int status = rs.getInt("status");
                                      Instant timestamp = rs.getTimestamp("timestamp").toInstant();

                                      MovedPoint movedPoint = movedMap.get(id);
                                      double lat = useOldCoords ? movedPoint.oldLat() : movedPoint.newLat();
                                      double lng = useOldCoords ? movedPoint.oldLng() : movedPoint.newLng();
                                      long effectiveH3Cell = useOldCoords ? movedPoint.oldH3Cell() : movedPoint.newH3Cell();

                                      return new PointData(userId, deviceId, id, lat, lng, effectiveH3Cell, status, timestamp);
                                  },
                                  ids.toArray()
        );
    }

    private boolean isNewCellForUser(long userId, Long deviceId, long h3Cell) {
        String checkSql = "SELECT COUNT(*) FROM h3_cells_stats WHERE user_id = ? AND device_id = ? AND h3_index = ?";
        Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, userId, deviceId, h3Cell);
        return count == null || count == 0;
    }

    private void processBatchForDeletion(List<Long> batchIds) {
        List<PointData> points = loadPointData(batchIds);

        for (PointData point : points) {
            log.trace("Deleting point: userId={}, deviceId={}, id={}, lat={}, lng={}, h3Cell={}, status={}",
                      point.userId, point.deviceid, point.id, point.lat, point.lng, point.h3Cell, point.status);

            Set<Long> parentCells = rocksDbService.getParentCells(point.h3Cell);

            for (Long parentCell : parentCells) {
                decrementCellAndCheckRemoval(point.userId, point.deviceid, parentCell);
            }
        }
    }

    private void decrementCellAndCheckRemoval(long userId, Long deviceId, long h3Cell) {
        String decrementSql = """
        UPDATE h3_cells_stats
        SET point_count = point_count - 1
                WHERE user_id = ? AND device_id = ? AND h3_index = ?
        """;

        int updatedRows = jdbcTemplate.update(decrementSql, userId, deviceId, h3Cell);

        if (updatedRows > 0) {
            String checkCountSql = "SELECT point_count FROM h3_cells_stats WHERE user_id = ? AND device_id = ? AND h3_index = ?";
            Integer pointCount = jdbcTemplate.queryForObject(checkCountSql, Integer.class, userId, deviceId, h3Cell);

            if (pointCount != null && pointCount <= 0) {
                jdbcTemplate.update("DELETE FROM h3_cells_stats WHERE user_id = ? AND device_id = ? AND h3_index = ?",
                                    userId, deviceId, h3Cell);

                int resolution = rocksDbService.getResolution(h3Cell);
                Set<Long> osmIds = rocksDbService.getOsmIds(h3Cell);
                for (Long osmId : osmIds) {
                    decrementAreaVisitedCells(userId, deviceId, osmId, resolution);
                }
            }
        }
    }

    private void decrementAreaVisitedCells(long userId, Long deviceId, long osmId, int resolution) {
        String decrementSql = """
        UPDATE h3_area_coverage_stats
        SET visited_cell_count = visited_cell_count - 1
                WHERE user_id = ? AND device_id = ? AND osm_id = ? AND h3_resolution = ?
        """;

        int updatedRows = jdbcTemplate.update(decrementSql, userId, deviceId, osmId, resolution);

        if (updatedRows > 0) {
            String checkSql = """
            SELECT visited_cell_count FROM h3_area_coverage_stats
                    WHERE user_id = ? AND device_id = ? AND osm_id = ? AND h3_resolution = ?
            """;
            Integer visitedCount = jdbcTemplate.queryForObject(checkSql, Integer.class,
                                                               userId, deviceId, osmId, resolution);

            if (visitedCount != null && visitedCount <= 0) {
                jdbcTemplate.update("DELETE FROM h3_area_coverage_stats WHERE user_id = ? AND device_id = ? AND osm_id = ? AND h3_resolution = ?",
                                    userId, deviceId, osmId, resolution);
            }
        }
    }

    private void processIncrement(List<CellIncrement> cellIncrements) {
        for (CellIncrement inc : cellIncrements) {
            long userId = inc.userId();
            Long deviceId = inc.deviceId();

            Set<Long> parentCells = rocksDbService.getParentCells(inc.h3Cell());

            for (Long parentCell : parentCells) {
                boolean isNewCell = isNewCellForUser(userId, deviceId, parentCell);

                String upsertCellSql = """
                        INSERT INTO h3_cells_stats (user_id, device_id, h3_index,
                                                    last_visited_at, point_count, first_visited_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT (user_id, device_id, h3_index) DO UPDATE SET
                            last_visited_at = GREATEST(h3_cells_stats.last_visited_at, ?),
                            point_count = h3_cells_stats.point_count + ?,
                            first_visited_at = LEAST(h3_cells_stats.first_visited_at, ?)
                        """;
                jdbcTemplate.update(upsertCellSql,
                                    userId, deviceId, parentCell,
                                    Timestamp.from(inc.lastVisitedAt()),
                                    inc.count(),
                                    Timestamp.from(inc.firstVisitedAt()),
                                    Timestamp.from(inc.lastVisitedAt()),
                                    inc.count(),
                                    Timestamp.from(inc.firstVisitedAt()));

                if (isNewCell) {
                    int resolution = rocksDbService.getResolution(parentCell);
                    Set<Long> osmIds = rocksDbService.getOsmIds(parentCell);
                    for (Long osmId : osmIds) {
                        incrementAreaVisitedCells(userId, deviceId, osmId, resolution);
                    }
                }
            }
        }
    }

    private record PointData(long userId, Long deviceid, long id, double lat, double lng, long h3Cell, int status,
                             Instant timestamp) {}

    public record MovedPoint(long id, double oldLat, double oldLng, double newLat, double newLng, long oldH3Cell, long newH3Cell) implements Serializable {}

    public record CellIncrement(long userId, Long deviceId, long h3Cell, int count, Instant lastVisitedAt,
                                Instant firstVisitedAt) implements Serializable {
    }

    public enum ChangeType {
        DELETION, PROMOTION, INCREMENT, INCREMENT_SOURCE, MOVEMENT
    }

    public static class TaskData extends JobContext<TaskData> {
        private final ChangeType changeType;
        private final List<Long> pointIds;
        private final List<MovedPoint> movedPoints;
        private final List<CellIncrement> cellIncrements;

        public TaskData(ChangeType changeType, List<Long> pointIds) {
            this(changeType, pointIds, List.of(), List.of());
        }

        public TaskData(ChangeType changeType, List<Long> pointIds, List<MovedPoint> movedPoints) {
            this(changeType, pointIds, movedPoints, List.of());
        }


        public TaskData(ChangeType changeType, List<Long> pointIds, List<MovedPoint> movedPoints, List<CellIncrement> cellIncrements) {
            this.changeType = changeType;
            this.pointIds = pointIds;
            this.movedPoints = movedPoints;
            this.cellIncrements = cellIncrements;
        }

        private TaskData(UUID jobId, UUID parentJobId, ChangeType changeType, List<Long> pointIds, List<MovedPoint> movedPoints, List<CellIncrement> cellIncrements) {
            super(jobId, parentJobId);
            this.changeType = changeType;
            this.pointIds = pointIds;
            this.movedPoints = movedPoints;
            this.cellIncrements = cellIncrements;
        }

        public static TaskData forPromotion(List<Long> newPromotedIds) {
            return new TaskData(ChangeType.PROMOTION, newPromotedIds, List.of(), List.of());
        }

        public static TaskData forDeletion(List<Long> deletedPointIds) {
            return new TaskData(ChangeType.DELETION, deletedPointIds, List.of(), List.of());
        }

        public static TaskData forMovement(List<MovedPoint> movedPoints) {
            return new TaskData(ChangeType.MOVEMENT, List.of(), movedPoints, List.of());
        }

        public static TaskData forIncrement(List<CellIncrement> cellIncrements) {
            return new TaskData(ChangeType.INCREMENT, List.of(), List.of(), cellIncrements);
        }

        @Override
        public TaskData withJobId(UUID jobId) {
            return new TaskData(jobId, parentJobId, changeType, pointIds, movedPoints, cellIncrements);
        }

        @Override
        public TaskData withParentJobId(UUID parentJobId) {
            return new TaskData(jobId, parentJobId, changeType, pointIds, movedPoints, cellIncrements);
        }
    }
}
