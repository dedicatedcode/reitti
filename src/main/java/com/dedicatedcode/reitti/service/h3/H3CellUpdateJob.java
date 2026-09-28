package com.dedicatedcode.reitti.service.h3;

import com.dedicatedcode.reitti.repository.JobMetadataRepository;
import com.dedicatedcode.reitti.service.JobContext;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.Serializable;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@DisallowConcurrentExecution
public class H3CellUpdateJob implements Job {
    private static final Logger log = LoggerFactory.getLogger(H3CellUpdateJob.class);
    private static final int MAX_STATEMENT_PARAMS = 30000;

    private final JdbcTemplate jdbcTemplate;
    private final RocksDBH3Service rocksDbService;
    private final JobSchedulingService jobSchedulingService;
    private final JobMetadataRepository jobMetadataRepository;

    @Value("${reitti.h3.update-batch-size:1000}")
    private int batchSize = 1000;

    public H3CellUpdateJob(JdbcTemplate jdbcTemplate,
                           RocksDBH3Service rocksDbService,
                           JobSchedulingService jobSchedulingService,
                           JobMetadataRepository jobMetadataRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.rocksDbService = rocksDbService;
        this.jobSchedulingService = jobSchedulingService;
        this.jobMetadataRepository = jobMetadataRepository;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        TaskData data = TaskData.fromJson((String) context.getMergedJobDataMap().get("data"));

        if (!rocksDbService.isAvailable()) {
            log.debug("RocksDB is not available yet. Deferring job.");
            if (!jobSchedulingService.defer(context, Duration.ofSeconds(5), "Waiting for RocksDB to become available")) {
                log.warn("Cannot defer untracked H3 cell update, dropping execution");
            }
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

        int effectiveBatchSize = Math.max(1, batchSize);
        List<Long> ids = data.pointIds;
        jobMetadataRepository.updateProgress(data.getJobId(), 0, ids.size(), "Updating H3 cells ...");

        int processed = 0;
        for (int i = 0; i < ids.size(); i += effectiveBatchSize) {
            int endIndex = Math.min(i + effectiveBatchSize, ids.size());
            List<Long> batch = ids.subList(i, endIndex);

            log.debug("Processing batch {}/{}: {} points",
                      (i / effectiveBatchSize) + 1,
                      (ids.size() + effectiveBatchSize - 1) / effectiveBatchSize,
                      batch.size());
            switch (data.changeType) {
                case DELETION -> processBatchForDeletion(batch);
                case PROMOTION -> processBatchForPromotion(batch);
            }
            processed += batch.size();
            jobMetadataRepository.updateProgress(data.getJobId(), processed, ids.size(), "Updating H3 cells ...");
        }
        jobMetadataRepository.updateProgress(data.getJobId(), processed, ids.size(), "Done");
    }

    private void processMovement(List<MovedPoint> movedPoints) {
        if (movedPoints.isEmpty()) {
            return;
        }
        applyDecrements(toParentAggregations(toRes12Aggregations(loadPointDataForMoved(movedPoints, true))));
        applyIncrements(toParentAggregations(toRes12Aggregations(loadPointDataForMoved(movedPoints, false))));
    }

    private void processBatchForPromotion(List<Long> batchIds) {
        List<PointData> points = loadPointData(batchIds);
        if (points.isEmpty()) {
            return;
        }
        applyIncrements(toParentAggregations(toRes12Aggregations(points)));
    }

    private void processBatchForDeletion(List<Long> batchIds) {
        List<PointData> points = loadPointData(batchIds);
        if (points.isEmpty()) {
            return;
        }
        applyDecrements(toParentAggregations(toRes12Aggregations(points)));
    }

    private void processIncrement(List<CellIncrement> cellIncrements) {
        Map<CellKey, ParentAgg> res12Aggregations = new HashMap<>();
        for (CellIncrement increment : cellIncrements) {
            res12Aggregations.merge(new CellKey(increment.userId(), increment.deviceId(), increment.h3Cell()),
                                    new ParentAgg(increment.userId(), increment.deviceId(), increment.h3Cell(),
                                                  increment.count(), increment.firstVisitedAt(), increment.lastVisitedAt()),
                                    ParentAgg::merge);
        }
        applyIncrements(toParentAggregations(res12Aggregations));
    }

    private Map<CellKey, ParentAgg> toRes12Aggregations(List<PointData> points) {
        Map<CellKey, ParentAgg> aggregations = new HashMap<>();
        for (PointData point : points) {
            aggregations.merge(new CellKey(point.userId(), point.deviceId(), point.h3Cell()),
                               new ParentAgg(point.userId(), point.deviceId(), point.h3Cell(), 1, point.timestamp(), point.timestamp()),
                               ParentAgg::merge);
        }
        return aggregations;
    }

    private Map<CellKey, ParentAgg> toParentAggregations(Map<CellKey, ParentAgg> res12Aggregations) {
        Map<CellKey, ParentAgg> aggregations = new HashMap<>();
        for (ParentAgg aggregation : res12Aggregations.values()) {
            for (Long parentCell : rocksDbService.getParentCells(aggregation.h3Cell())) {
                aggregations.merge(new CellKey(aggregation.userId(), aggregation.deviceId(), parentCell),
                                   new ParentAgg(aggregation.userId(), aggregation.deviceId(), parentCell,
                                                 aggregation.count(), aggregation.firstVisitedAt(), aggregation.lastVisitedAt()),
                                   ParentAgg::merge);
            }
        }
        return aggregations;
    }

    private void applyIncrements(Map<CellKey, ParentAgg> parentAggregations) {
        if (parentAggregations.isEmpty()) {
            return;
        }
        Set<CellKey> existingCells = findExistingCells(parentAggregations.keySet());
        upsertCellStats(parentAggregations.values());
        upsertNewAreaStats(parentAggregations.values(), existingCells);
    }

    private void applyDecrements(Map<CellKey, ParentAgg> parentAggregations) {
        if (parentAggregations.isEmpty()) {
            return;
        }
        decrementCellStats(parentAggregations.values());
        List<CellKey> emptiedCells = deleteEmptiedCells(parentAggregations.keySet());
        if (emptiedCells.isEmpty()) {
            return;
        }
        Map<AreaKey, AreaAgg> areaDecrements = new HashMap<>();
        for (CellKey cell : emptiedCells) {
            int resolution = rocksDbService.getResolution(cell.h3Cell());
            for (Long osmId : rocksDbService.getOsmIds(cell.h3Cell())) {
                areaDecrements.merge(new AreaKey(cell.userId(), cell.deviceId(), osmId, resolution),
                                     new AreaAgg(1, 0),
                                     (a, b) -> new AreaAgg(a.count() + b.count(), a.totalCells()));
            }
        }
        decrementAreaStats(areaDecrements);
        deleteEmptiedAreas(areaDecrements.keySet());
    }

    private void upsertCellStats(Collection<ParentAgg> aggregations) {
        String sqlTemplate = """
                INSERT INTO h3_cells_stats (user_id, device_id, h3_index, last_visited_at, point_count, first_visited_at)
                VALUES %s
                ON CONFLICT (user_id, device_id, h3_index) DO UPDATE SET
                    last_visited_at = GREATEST(h3_cells_stats.last_visited_at, EXCLUDED.last_visited_at),
                    point_count = h3_cells_stats.point_count + EXCLUDED.point_count,
                    first_visited_at = LEAST(h3_cells_stats.first_visited_at, EXCLUDED.first_visited_at)
                """;
        for (List<ParentAgg> chunk : partition(aggregations, MAX_STATEMENT_PARAMS / 6)) {
            String sql = sqlTemplate.formatted(valuesPlaceholders(chunk.size(), "(?, ?, ?, ?, ?, ?)"));
            Object[] args = new Object[chunk.size() * 6];
            int i = 0;
            for (ParentAgg aggregation : chunk) {
                args[i++] = aggregation.userId();
                args[i++] = aggregation.deviceId();
                args[i++] = aggregation.h3Cell();
                args[i++] = Timestamp.from(aggregation.lastVisitedAt());
                args[i++] = aggregation.count();
                args[i++] = Timestamp.from(aggregation.firstVisitedAt());
            }
            jdbcTemplate.update(sql, args);
        }
    }

    private Set<CellKey> findExistingCells(Set<CellKey> cells) {
        Set<CellKey> existing = new HashSet<>();
        String sqlTemplate = """
                SELECT v.user_id, v.device_id, v.h3_index
                FROM (VALUES %s) AS v(user_id, device_id, h3_index)
                JOIN h3_cells_stats s ON s.user_id = v.user_id
                     AND s.h3_index = v.h3_index
                     AND s.device_id IS NOT DISTINCT FROM v.device_id
                """;
        for (List<CellKey> chunk : partition(cells, MAX_STATEMENT_PARAMS / 3)) {
            String sql = sqlTemplate.formatted(valuesPlaceholders(chunk.size(), "(CAST(? AS BIGINT), CAST(? AS BIGINT), CAST(? AS BIGINT))"));
            Object[] args = new Object[chunk.size() * 3];
            int i = 0;
            for (CellKey cell : chunk) {
                args[i++] = cell.userId();
                args[i++] = cell.deviceId();
                args[i++] = cell.h3Cell();
            }
            existing.addAll(jdbcTemplate.query(sql,
                                               (rs, _) -> new CellKey(rs.getLong("user_id"),
                                                                      rs.getObject("device_id", Long.class),
                                                                      rs.getLong("h3_index")),
                                               args));
        }
        return existing;
    }

    private void upsertNewAreaStats(Collection<ParentAgg> aggregations, Set<CellKey> existingCells) {
        Map<AreaKey, AreaAgg> visitedAreas = new HashMap<>();
        for (ParentAgg aggregation : aggregations) {
            if (existingCells.contains(new CellKey(aggregation.userId(), aggregation.deviceId(), aggregation.h3Cell()))) {
                continue;
            }
            int resolution = rocksDbService.getResolution(aggregation.h3Cell());
            for (Long osmId : rocksDbService.getOsmIds(aggregation.h3Cell())) {
                int totalCells = rocksDbService.getTotalCells(osmId, resolution);
                if (totalCells <= 0) {
                    continue;
                }
                visitedAreas.merge(new AreaKey(aggregation.userId(), aggregation.deviceId(), osmId, resolution),
                                   new AreaAgg(1, totalCells),
                                   (a, b) -> new AreaAgg(a.count() + b.count(), b.totalCells()));
            }
        }
        if (visitedAreas.isEmpty()) {
            return;
        }
        String sqlTemplate = """
                INSERT INTO h3_area_coverage_stats (user_id, device_id, osm_id, h3_resolution, visited_cell_count, total_cell_count)
                VALUES %s
                ON CONFLICT (user_id, device_id, osm_id, h3_resolution) DO UPDATE SET
                    visited_cell_count = h3_area_coverage_stats.visited_cell_count + EXCLUDED.visited_cell_count,
                    total_cell_count = EXCLUDED.total_cell_count
                """;
        for (List<Map.Entry<AreaKey, AreaAgg>> chunk : partition(visitedAreas.entrySet(), MAX_STATEMENT_PARAMS / 6)) {
            String sql = sqlTemplate.formatted(valuesPlaceholders(chunk.size(), "(?, ?, ?, ?, ?, ?)"));
            Object[] args = new Object[chunk.size() * 6];
            int i = 0;
            for (Map.Entry<AreaKey, AreaAgg> entry : chunk) {
                AreaKey area = entry.getKey();
                AreaAgg visited = entry.getValue();
                args[i++] = area.userId();
                args[i++] = area.deviceId();
                args[i++] = area.osmId();
                args[i++] = area.resolution();
                args[i++] = visited.count();
                args[i++] = visited.totalCells();
            }
            jdbcTemplate.update(sql, args);
        }
    }

    private void decrementCellStats(Collection<ParentAgg> aggregations) {
        String sqlTemplate = """
                UPDATE h3_cells_stats s
                SET point_count = s.point_count - v.cnt
                FROM (VALUES %s) AS v(user_id, device_id, h3_index, cnt)
                WHERE s.user_id = v.user_id
                  AND s.h3_index = v.h3_index
                  AND s.device_id IS NOT DISTINCT FROM v.device_id
                """;
        for (List<ParentAgg> chunk : partition(aggregations, MAX_STATEMENT_PARAMS / 4)) {
            String sql = sqlTemplate.formatted(valuesPlaceholders(chunk.size(), "(CAST(? AS BIGINT), CAST(? AS BIGINT), CAST(? AS BIGINT), CAST(? AS INT))"));
            Object[] args = new Object[chunk.size() * 4];
            int i = 0;
            for (ParentAgg aggregation : chunk) {
                args[i++] = aggregation.userId();
                args[i++] = aggregation.deviceId();
                args[i++] = aggregation.h3Cell();
                args[i++] = aggregation.count();
            }
            jdbcTemplate.update(sql, args);
        }
    }

    private List<CellKey> deleteEmptiedCells(Set<CellKey> cells) {
        List<CellKey> emptied = new ArrayList<>();
        String sqlTemplate = """
                DELETE FROM h3_cells_stats s
                USING (VALUES %s) AS v(user_id, device_id, h3_index)
                WHERE s.user_id = v.user_id
                  AND s.h3_index = v.h3_index
                  AND s.device_id IS NOT DISTINCT FROM v.device_id
                  AND s.point_count <= 0
                RETURNING s.user_id, s.device_id, s.h3_index
                """;
        for (List<CellKey> chunk : partition(cells, MAX_STATEMENT_PARAMS / 3)) {
            String sql = sqlTemplate.formatted(valuesPlaceholders(chunk.size(), "(CAST(? AS BIGINT), CAST(? AS BIGINT), CAST(? AS BIGINT))"));
            Object[] args = new Object[chunk.size() * 3];
            int i = 0;
            for (CellKey cell : chunk) {
                args[i++] = cell.userId();
                args[i++] = cell.deviceId();
                args[i++] = cell.h3Cell();
            }
            emptied.addAll(jdbcTemplate.query(sql,
                                              (rs, _) -> new CellKey(rs.getLong("user_id"),
                                                                     rs.getObject("device_id", Long.class),
                                                                     rs.getLong("h3_index")),
                                              args));
        }
        return emptied;
    }

    private void decrementAreaStats(Map<AreaKey, AreaAgg> areaDecrements) {
        String sqlTemplate = """
                UPDATE h3_area_coverage_stats s
                SET visited_cell_count = s.visited_cell_count - v.cnt
                FROM (VALUES %s) AS v(user_id, device_id, osm_id, h3_resolution, cnt)
                WHERE s.user_id = v.user_id
                  AND s.osm_id = v.osm_id
                  AND s.h3_resolution = v.h3_resolution
                  AND s.device_id IS NOT DISTINCT FROM v.device_id
                """;
        for (List<Map.Entry<AreaKey, AreaAgg>> chunk : partition(areaDecrements.entrySet(), MAX_STATEMENT_PARAMS / 5)) {
            String sql = sqlTemplate.formatted(valuesPlaceholders(chunk.size(), "(CAST(? AS BIGINT), CAST(? AS BIGINT), CAST(? AS BIGINT), CAST(? AS INT), CAST(? AS INT))"));
            Object[] args = new Object[chunk.size() * 5];
            int i = 0;
            for (Map.Entry<AreaKey, AreaAgg> entry : chunk) {
                args[i++] = entry.getKey().userId();
                args[i++] = entry.getKey().deviceId();
                args[i++] = entry.getKey().osmId();
                args[i++] = entry.getKey().resolution();
                args[i++] = entry.getValue().count();
            }
            jdbcTemplate.update(sql, args);
        }
    }

    private void deleteEmptiedAreas(Set<AreaKey> areas) {
        String sqlTemplate = """
                DELETE FROM h3_area_coverage_stats s
                USING (VALUES %s) AS v(user_id, device_id, osm_id, h3_resolution)
                WHERE s.user_id = v.user_id
                  AND s.osm_id = v.osm_id
                  AND s.h3_resolution = v.h3_resolution
                  AND s.device_id IS NOT DISTINCT FROM v.device_id
                  AND s.visited_cell_count <= 0
                """;
        for (List<AreaKey> chunk : partition(areas, MAX_STATEMENT_PARAMS / 4)) {
            String sql = sqlTemplate.formatted(valuesPlaceholders(chunk.size(), "(CAST(? AS BIGINT), CAST(? AS BIGINT), CAST(? AS BIGINT), CAST(? AS INT))"));
            Object[] args = new Object[chunk.size() * 4];
            int i = 0;
            for (AreaKey area : chunk) {
                args[i++] = area.userId();
                args[i++] = area.deviceId();
                args[i++] = area.osmId();
                args[i++] = area.resolution();
            }
            jdbcTemplate.update(sql, args);
        }
    }

    private List<PointData> loadPointData(List<Long> batchIds) {
        String placeholders = String.join(",", Collections.nCopies(batchIds.size(), "?"));
        String sql = "SELECT rsp.user_id, rsp.device_id, rsp.id, rsp.h3_cell, rsp.timestamp " +
                "FROM raw_source_points rsp " +
                "JOIN users u ON rsp.user_id = u.id " +
                "WHERE u.user_type != 'LIVE_DATA_ONLY' AND rsp.h3_cell IS NOT NULL AND rsp.id IN (" + placeholders + ")";

        return jdbcTemplate.query(sql,
                                  (rs, _) -> new PointData(rs.getLong("user_id"),
                                                           rs.getObject("device_id", Long.class),
                                                           rs.getLong("id"),
                                                           rs.getLong("h3_cell"),
                                                           rs.getTimestamp("timestamp").toInstant()),
                                  batchIds.toArray());
    }

    private List<PointData> loadPointDataForMoved(List<MovedPoint> movedPoints, boolean useOldCoords) {
        List<Long> ids = movedPoints.stream().map(MovedPoint::id).toList();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = "SELECT rsp.user_id, rsp.device_id, rsp.id, rsp.timestamp " +
                "FROM raw_source_points rsp " +
                "JOIN users u ON rsp.user_id = u.id " +
                "WHERE u.user_type != 'LIVE_DATA_ONLY' AND rsp.id IN (" + placeholders + ")";

        Map<Long, MovedPoint> movedMap = movedPoints.stream().collect(Collectors.toMap(MovedPoint::id, mp -> mp));

        return jdbcTemplate.query(sql,
                                  (rs, _) -> {
                                      MovedPoint movedPoint = movedMap.get(rs.getLong("id"));
                                      long h3Cell = useOldCoords ? movedPoint.oldH3Cell() : movedPoint.newH3Cell();
                                      return new PointData(rs.getLong("user_id"),
                                                           rs.getObject("device_id", Long.class),
                                                           rs.getLong("id"),
                                                           h3Cell,
                                                           rs.getTimestamp("timestamp").toInstant());
                                  },
                                  ids.toArray());
    }

    private static String valuesPlaceholders(int rows, String rowTemplate) {
        return String.join(",", Collections.nCopies(rows, rowTemplate));
    }

    private static <T> List<List<T>> partition(Collection<T> items, int chunkSize) {
        List<T> list = new ArrayList<>(items);
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += chunkSize) {
            chunks.add(list.subList(i, Math.min(i + chunkSize, list.size())));
        }
        return chunks;
    }

    private record PointData(long userId, Long deviceId, long id, long h3Cell, Instant timestamp) {}

    private record CellKey(long userId, Long deviceId, long h3Cell) {}

    private record ParentAgg(long userId, Long deviceId, long h3Cell, int count, Instant firstVisitedAt, Instant lastVisitedAt) {
        private ParentAgg merge(ParentAgg other) {
            return new ParentAgg(userId, deviceId, h3Cell,
                                 count + other.count,
                                 min(firstVisitedAt, other.firstVisitedAt),
                                 max(lastVisitedAt, other.lastVisitedAt));
        }

        private static Instant min(Instant a, Instant b) {
            return a.isBefore(b) ? a : b;
        }

        private static Instant max(Instant a, Instant b) {
            return a.isAfter(b) ? a : b;
        }
    }

    private record AreaKey(long userId, Long deviceId, long osmId, int resolution) {}

    private record AreaAgg(int count, int totalCells) {}

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

        @JsonCreator
        private TaskData(@JsonProperty("jobId") UUID jobId,
                         @JsonProperty("parentJobId") UUID parentJobId,
                         @JsonProperty("changeType") ChangeType changeType,
                         @JsonProperty("pointIds") List<Long> pointIds,
                         @JsonProperty("movedPoints") List<MovedPoint> movedPoints,
                         @JsonProperty("cellIncrements") List<CellIncrement> cellIncrements) {
            super(jobId, parentJobId);
            this.changeType = changeType;
            this.pointIds = pointIds;
            this.movedPoints = movedPoints;
            this.cellIncrements = cellIncrements;
        }

        public static TaskData fromJson(String json) {
            return JobContext.fromJson(json, TaskData.class);
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
