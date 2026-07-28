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

import java.io.Serializable;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

        if (data.changeType == ChangeType.MOVEMENT) {
            log.debug("Processing movement for {} points", data.movedPoints.size());
            processMovement(data.movedPoints);
            return;
        }

        if (data.changeType == ChangeType.DECREMENT) {
            log.debug("Processing decrement for {} cells", data.cellDecrements.size());
            processDecrement(data.cellDecrements);
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
        // 1. Delete old locations
        List<PointData> oldPoints = loadPointDataForMoved(movedPoints, true);
        for (PointData point : oldPoints) {
            log.debug("Processing moved point (deletion): userId={}, deviceId={}, id={}, lat={}, lng={}, h3Cell={}, status={}",
                      point.userId, point.deviceid, point.id, point.lat, point.lng, point.h3Cell, point.status);

            Set<Long> h3Cells = rocksDbService.getCellsForPoint(point.lat, point.lng);
            for (Long h3Cell : h3Cells) {
                decrementCellAndCheckRemoval(point.userId, h3Cell, point);
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
        log.debug("Processing point: userId={}, deviceId={}, id={}, lat={}, lng={}, h3Cell={}, status={}",
                  point.userId, point.deviceid, point.id, point.lat, point.lng, point.h3Cell, point.status);

        Set<Long> h3Cells = rocksDbService.getCellsForPoint(point.lat, point.lng);

        for (Long h3Cell : h3Cells) {
            boolean isNewCell = isNewCellForUser(point.userId, h3Cell);

            String upsertCellSql = """
            INSERT INTO h3_cells_stats (user_id, device_id, h3_index, last_visited_at, point_count)
            VALUES (?, ?, ?, ?, 1)
            ON CONFLICT (user_id, device_id, h3_index) DO UPDATE SET
                last_visited_at = GREATEST(h3_cells_stats.last_visited_at, ?),
                point_count = h3_cells_stats.point_count + 1
            """;

            jdbcTemplate.update(upsertCellSql, point.userId, point.deviceid, h3Cell,
                                Timestamp.from(point.timestamp), Timestamp.from(point.timestamp));

            if (isNewCell) {
                updateAreaCoverageForCell(point.userId, point.deviceid, h3Cell);
            }
        }
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
        String sql = "SELECT user_id, device_id, id, h3_cell, status, timestamp " +
                "FROM raw_source_points WHERE id IN (" + placeholders + ")";

        Map<Long, MovedPoint> movedMap = movedPoints.stream().collect(Collectors.toMap(MovedPoint::id, mp -> mp));

        return jdbcTemplate.query(sql,
                                  (rs, _) -> {
                                      long userId = rs.getLong("user_id");
                                      Long deviceId = rs.getObject("device_id", Long.class);
                                      long id = rs.getLong("id");
                                      long h3Cell = rs.getLong("h3_cell");
                                      int status = rs.getInt("status");
                                      Instant timestamp = rs.getTimestamp("timestamp").toInstant();

                                      MovedPoint movedPoint = movedMap.get(id);
                                      double lat = useOldCoords ? movedPoint.oldLat() : movedPoint.newLat();
                                      double lng = useOldCoords ? movedPoint.oldLng() : movedPoint.newLng();

                                      return new PointData(userId, deviceId, id, lat, lng, h3Cell, status, timestamp);
                                  },
                                  ids.toArray()
        );
    }

    private boolean isNewCellForUser(long userId, long h3Cell) {
        String checkSql = "SELECT COUNT(*) FROM h3_cells_stats WHERE user_id = ? AND h3_index = ?";
        Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, userId, h3Cell);
        return count == null || count == 0;
    }

    private void updateAreaCoverageForCell(long userId, Long deviceId, long h3Cell) {
        // Get OSM boundaries that contain this H3 cell
        List<RocksDBH3Service.CellWithBoundaries> cellsWithBoundaries =
                rocksDbService.getCellsWithBoundaries(h3Cell);

        for (RocksDBH3Service.CellWithBoundaries cellWithBoundary : cellsWithBoundaries) {
            int resolution = cellWithBoundary.resolution();

            for (Long osmId : cellWithBoundary.osmIds()) {
                int totalCells = rocksDbService.getTotalCells(osmId);

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
                                        userId, deviceId, osmId, resolution,
                                        totalCells, totalCells);
                }
            }
        }
    }

    private void processBatchForDeletion(List<Long> batchIds) {
        List<PointData> points = loadPointData(batchIds);

        for (PointData point : points) {
            log.debug("Deleting point: userId={}, deviceId={}, id={}, lat={}, lng={}, h3Cell={}, status={}",
                      point.userId, point.deviceid, point.id, point.lat, point.lng, point.h3Cell, point.status);

            Set<Long> h3Cells = rocksDbService.getCellsForPoint(point.lat, point.lng);

            for (Long h3Cell : h3Cells) {
                decrementCellAndCheckRemoval(point.userId, h3Cell, point);
            }
        }
    }


    private void processDecrement(List<CellDecrement> cellDecrements) {
        for (CellDecrement decrement : cellDecrements) {
            long userId = decrement.userId();
            long h3Cell = decrement.h3Cell();
            int count = decrement.count();

            String decrementSql = """
            UPDATE h3_cells_stats
            SET point_count = point_count - ?
            WHERE user_id = ? AND h3_index = ?
            """;

            int updatedRows = jdbcTemplate.update(decrementSql, count, userId, h3Cell);

            if (updatedRows > 0) {
                String checkCountSql = "SELECT point_count FROM h3_cells_stats WHERE user_id = ? AND h3_index = ?";
                Integer pointCount = jdbcTemplate.queryForObject(checkCountSql, Integer.class, userId, h3Cell);

                if (pointCount != null && pointCount <= 0) {
                    String deleteCellSql = "DELETE FROM h3_cells_stats WHERE user_id = ? AND h3_index = ?";
                    jdbcTemplate.update(deleteCellSql, userId, h3Cell);

                    decrementAreaCoverageForCell(userId, h3Cell);
                }
            }
        }
    }

    private void processIncrement(List<CellIncrement> cellIncrements) {
        for (CellIncrement increment : cellIncrements) {
            long userId = increment.userId();
            Long deviceId = increment.deviceId();
            long h3Cell = increment.h3Cell();
            int count = increment.count();
            Instant timestamp = increment.timestamp();

            boolean isNewCell = isNewCellForUser(userId, h3Cell);

            String upsertCellSql = """
            INSERT INTO h3_cells_stats (user_id, device_id, h3_index, last_visited_at, point_count)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (user_id, device_id, h3_index) DO UPDATE SET
                last_visited_at = GREATEST(h3_cells_stats.last_visited_at, ?),
                point_count = h3_cells_stats.point_count + ?
            """;

            jdbcTemplate.update(upsertCellSql, userId, deviceId, h3Cell,
                                Timestamp.from(timestamp), count, Timestamp.from(timestamp), count);

            if (isNewCell) {
                updateAreaCoverageForCell(userId, deviceId, h3Cell);
            }
        }
    }

    private void decrementCellAndCheckRemoval(long userId, long h3Cell, PointData point) {
        String decrementSql = """
        UPDATE h3_cells_stats
        SET point_count = point_count - 1
        WHERE user_id = ? AND h3_index = ?
        """;

        int updatedRows = jdbcTemplate.update(decrementSql, userId, h3Cell);

        if (updatedRows > 0) {
            String checkCountSql = "SELECT point_count FROM h3_cells_stats WHERE user_id = ? AND h3_index = ?";
            Integer pointCount = jdbcTemplate.queryForObject(checkCountSql, Integer.class, userId, h3Cell);

            if (pointCount != null && pointCount <= 0) {
                String deleteCellSql = "DELETE FROM h3_cells_stats WHERE user_id = ? AND h3_index = ?";
                jdbcTemplate.update(deleteCellSql, userId, h3Cell);

                decrementAreaCoverageForCell(point.userId, h3Cell);
            }
        }
    }

    private void decrementAreaCoverageForCell(long userId, long h3Cell) {
        List<RocksDBH3Service.CellWithBoundaries> cellsWithBoundaries =
                rocksDbService.getCellsWithBoundaries(h3Cell);

        for (RocksDBH3Service.CellWithBoundaries cellWithBoundary : cellsWithBoundaries) {
            int resolution = cellWithBoundary.resolution();

            for (Long osmId : cellWithBoundary.osmIds()) {
                String decrementAreaSql = """
                UPDATE h3_area_coverage_stats
                SET visited_cell_count = visited_cell_count - 1
                WHERE user_id = ? AND osm_id = ? AND h3_resolution = ?
                """;

                int updatedRows = jdbcTemplate.update(decrementAreaSql, userId, osmId, resolution);

                if (updatedRows > 0) {
                    String checkVisitedSql = """
                    SELECT visited_cell_count
                    FROM h3_area_coverage_stats
                    WHERE user_id = ? AND osm_id = ? AND h3_resolution = ?
                    """;

                    Integer visitedCount = jdbcTemplate.queryForObject(checkVisitedSql, Integer.class,
                                                                       userId, osmId, resolution);

                    if (visitedCount != null && visitedCount <= 0) {
                        String deleteAreaSql = """
                        DELETE FROM h3_area_coverage_stats
                        WHERE user_id = ? AND osm_id = ? AND h3_resolution = ?
                        """;
                        jdbcTemplate.update(deleteAreaSql, userId, osmId, resolution);
                    }
                }
            }
        }
    }

    private record PointData(long userId, Long deviceid, long id, double lat, double lng, long h3Cell, int status,
                             Instant timestamp) {}

    public record MovedPoint(long id, double oldLat, double oldLng, double newLat, double newLng) implements Serializable {}

    public record CellDecrement(long userId, long h3Cell, int count) implements Serializable {}

    public record CellIncrement(long userId, Long deviceId, long h3Cell, int count, Instant timestamp) implements Serializable {}

    public enum ChangeType {
        DELETION, PROMOTION, DECREMENT, INCREMENT, MOVEMENT
    }

    public static class TaskData extends JobContext<TaskData> {
        private final ChangeType changeType;
        private final List<Long> pointIds;
        private final List<MovedPoint> movedPoints;
        private final List<CellDecrement> cellDecrements;
        private final List<CellIncrement> cellIncrements;

        public TaskData(ChangeType changeType, List<Long> pointIds) {
            this(changeType, pointIds, List.of(), List.of(), List.of());
        }

        public TaskData(ChangeType changeType, List<Long> pointIds, List<MovedPoint> movedPoints) {
            this(changeType, pointIds, movedPoints, List.of(), List.of());
        }

        public TaskData(ChangeType changeType, List<Long> pointIds, List<MovedPoint> movedPoints, List<CellDecrement> cellDecrements) {
            this(changeType, pointIds, movedPoints, cellDecrements, List.of());
        }

        public TaskData(ChangeType changeType, List<Long> pointIds, List<MovedPoint> movedPoints, List<CellDecrement> cellDecrements, List<CellIncrement> cellIncrements) {
            this.changeType = changeType;
            this.pointIds = pointIds;
            this.movedPoints = movedPoints;
            this.cellDecrements = cellDecrements;
            this.cellIncrements = cellIncrements;
        }

        private TaskData(UUID jobId, UUID parentJobId, ChangeType changeType, List<Long> pointIds, List<MovedPoint> movedPoints, List<CellDecrement> cellDecrements, List<CellIncrement> cellIncrements) {
            super(jobId, parentJobId);
            this.changeType = changeType;
            this.pointIds = pointIds;
            this.movedPoints = movedPoints;
            this.cellDecrements = cellDecrements;
            this.cellIncrements = cellIncrements;
        }

        public static TaskData forPromotion(List<Long> newPromotedIds) {
            return new TaskData(ChangeType.PROMOTION, newPromotedIds, List.of(), List.of(), List.of());
        }

        public static TaskData forDeletion(List<Long> deletedPointIds) {
            return new TaskData(ChangeType.DELETION, deletedPointIds, List.of(), List.of(), List.of());
        }

        public static TaskData forMovement(List<MovedPoint> movedPoints) {
            return new TaskData(ChangeType.MOVEMENT, List.of(), movedPoints, List.of(), List.of());
        }

        public static TaskData forDecrement(List<CellDecrement> cellDecrements) {
            return new TaskData(ChangeType.DECREMENT, List.of(), List.of(), cellDecrements, List.of());
        }

        public static TaskData forIncrement(List<CellIncrement> cellIncrements) {
            return new TaskData(ChangeType.INCREMENT, List.of(), List.of(), List.of(), cellIncrements);
        }
        @Override
        public TaskData withJobId(UUID jobId) {
            return new TaskData(jobId, parentJobId, changeType, pointIds, movedPoints, cellDecrements, cellIncrements);
        }

        @Override
        public TaskData withParentJobId(UUID parentJobId) {
            return new TaskData(jobId, parentJobId, changeType, pointIds, movedPoints, cellDecrements, cellIncrements);
        }
    }
}
