package com.dedicatedcode.reitti.service.h3;

import com.dedicatedcode.reitti.dto.workbench.MovedPointDto;
import com.dedicatedcode.reitti.model.CoverageInformation;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.PointReaderWriter;
import com.dedicatedcode.reitti.service.SpatialCoverageService;
import com.dedicatedcode.reitti.service.jobs.JobSchedulingService;
import com.dedicatedcode.reitti.service.jobs.JobType;
import com.uber.h3core.H3Core;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.WKBReader;
import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
@ConditionalOnProperty(name = "reitti.h3.enabled", havingValue = "true")
public class H3SpatialCoverageService implements SpatialCoverageService {
    private static final Logger log = LoggerFactory.getLogger(H3SpatialCoverageService.class);

    private final H3Core h3;
    private final JdbcTemplate jdbcTemplate;
    private final JobSchedulingService jobSchedulingService;
    private final JobDetail h3CellUpdateJob;
    private final PointReaderWriter pointReaderWriter;
    private final RocksDBH3Service rocksDBService;

    public H3SpatialCoverageService(JdbcTemplate jdbcTemplate,
                                    JobSchedulingService jobSchedulingService,
                                    @Qualifier("h3CellUpdateTask") JobDetail h3CellUpdateJob,
                                    PointReaderWriter pointReaderWriter,
                                    RocksDBH3Service rocksDBService) throws IOException {
        this.jdbcTemplate = jdbcTemplate;
        this.jobSchedulingService = jobSchedulingService;
        this.h3CellUpdateJob = h3CellUpdateJob;
        this.pointReaderWriter = pointReaderWriter;
        this.rocksDBService = rocksDBService;
        this.h3 = H3Core.newInstance();
    }

    @Override
    public Long getLevelCellForPoint(double latitude, double longitude, int resolution) {
        return h3.latLngToCell(latitude, longitude, resolution);
    }

    @Override
    public void postPromotion(List<Long> insertedIds) {
        jobSchedulingService.enqueueTaskAfterCommit(
                h3CellUpdateJob,
                H3CellUpdateJob.TaskData.forPromotion(insertedIds),
                JobSchedulingService.Metadata.builder()
                        .jobType(JobType.H3_CELL_UPDATE)
                        .friendlyName("Updating H3 Spatial Statistics")
                        .build()
        );
    }

    @Override
    public void postDeletion(List<Long> deletedPointIds) {
        this.jobSchedulingService.enqueueTaskAfterCommit(h3CellUpdateJob,
                                              H3CellUpdateJob.TaskData.forDeletion(deletedPointIds),
                                              JobSchedulingService.Metadata.builder()
                                                      .jobType(JobType.H3_CELL_UPDATE)
                                                      .friendlyName("Updating H3 Spatial Statistics")
                                                      .build()
        );
    }

    @Override
    public void preMove(List<MovedPointDto> movedPoints) {
        List<H3CellUpdateJob.MovedPoint> points = movedPoints.stream().map(movedPointDto -> {
            List<H3CellUpdateJob.MovedPoint> result = this.jdbcTemplate.query("SELECT ST_AsText(geom) AS geom_wkt, h3_cell FROM raw_source_points WHERE id = ? ", (rs, rowNum) -> {
                GeoPoint geomWkt = pointReaderWriter.read(rs.getString("geom_wkt"));
                long oldH3Cell = rs.getLong("h3_cell");
                long newH3Cell = h3.latLngToCell(movedPointDto.getLat(), movedPointDto.getLng(), 12);
                return new H3CellUpdateJob.MovedPoint(movedPointDto.getSourceId(), geomWkt.latitude(), geomWkt.longitude(), movedPointDto.getLat(), movedPointDto.getLng(), oldH3Cell, newH3Cell);
            }, movedPointDto.getSourceId());
            if (result.isEmpty()) {
                return null;
            } else {
                return result.getFirst();
            }
        }).filter(Objects::nonNull).toList();
        this.jobSchedulingService.enqueueTaskAfterCommit(h3CellUpdateJob,
                                              H3CellUpdateJob.TaskData.forMovement(points),
                                              JobSchedulingService.Metadata.builder()
                                                      .jobType(JobType.H3_CELL_UPDATE)
                                                      .friendlyName("Updating H3 Spatial Statistics")
                                                      .build()
        );
    }

    @Override
    public void postSourceRecalculation(List<Long> sourcePointIds) {
        if (sourcePointIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", Collections.nCopies(sourcePointIds.size(), "?"));
        String sql = "SELECT user_id, device_id, h3_cell, COUNT(*) as count, MAX(timestamp) as max_ts, MIN(timestamp) as min_ts FROM raw_source_points WHERE id IN (" + placeholders + ") GROUP BY user_id, device_id, h3_cell";
        List<H3CellUpdateJob.CellIncrement> increments = jdbcTemplate.query(sql,
                                                                            (rs, rowNum) -> new H3CellUpdateJob.CellIncrement(
                                                                                    rs.getLong("user_id"),
                                                                                    rs.getLong("device_id"),
                                                                                    rs.getLong("h3_cell"),
                                                                                    rs.getInt("count"),
                                                                                    rs.getTimestamp("max_ts").toInstant(),
                                                                                    rs.getTimestamp("min_ts").toInstant()),
                                                                            sourcePointIds.toArray());

        if (!increments.isEmpty()) {
            this.jobSchedulingService.enqueueTaskAfterCommit(h3CellUpdateJob,
                                                  H3CellUpdateJob.TaskData.forIncrement(increments),
                                                  JobSchedulingService.Metadata.builder()
                                                          .jobType(JobType.H3_CELL_UPDATE)
                                                          .friendlyName("Updating H3 Spatial Statistics")
                                                          .build()
            );
        }
    }

    @Override
    public Optional<CoverageInformation> getCoverageInformation(User user, long osmId, Locale locale) {
        List<Long> allVisitedCells = jdbcTemplate.queryForList(
                "SELECT DISTINCT h3_cell FROM v_source_stream WHERE user_id = ?",
                Long.class, user.getId());

        Map<Integer, Set<Long>> resolutionToCellIds = new HashMap<>();
        for (Long cell : allVisitedCells) {
            List<RocksDBH3Service.CellWithBoundaries> boundaries =
                    rocksDBService.getCellsWithBoundaries(cell);
            for (RocksDBH3Service.CellWithBoundaries cwb : boundaries) {
                if (cwb.osmIds().contains(osmId)) {
                    resolutionToCellIds
                            .computeIfAbsent(cwb.resolution(), r -> new HashSet<>())
                            .add(cwb.cellId());
                }
            }
        }

        // Find the finest supported resolution for which a total exists
        String totalSql = "SELECT total_cell_count FROM h3_area_coverage_stats " +
                "WHERE osm_id = ? AND h3_resolution = ? LIMIT 1";
        int totalCells = 0;
        int bestResolution = -1;
        List<Integer> sortedResolutions = new ArrayList<>(RocksDBH3Service.SUPPORTED_RESOLUTIONS);
        sortedResolutions.sort(Collections.reverseOrder());
        for (int res : sortedResolutions) {
            List<Integer> totals = jdbcTemplate.queryForList(totalSql, Integer.class, osmId, res);
            if (!totals.isEmpty()) {
                totalCells = totals.getFirst();
                bestResolution = res;
                break;
            }
        }

        if (bestResolution == -1) {
            return Optional.empty();
        }

        int visitedCells = resolutionToCellIds.getOrDefault(bestResolution, new HashSet<>()).size();
        double percentage = totalCells > 0 ? (double) visitedCells / totalCells * 100.0 : 0.0;

        Set<Long> visitedCellSet = resolutionToCellIds.getOrDefault(bestResolution, new HashSet<>());
        List<Long> visitedCellIds = new ArrayList<>(visitedCellSet);

        return Optional.of(new CoverageInformation(
                osmId,
                getLocalizedName(osmId, locale),
                totalCells,
                visitedCellIds.size(),
                percentage,
                bestResolution,
                rocksDBService.getAdminLevel(osmId),
                visitedCellIds
        ));
    }

    @Override
    public Optional<CoverageInformation> getCoverageInformation(User user, Device device, long osmId, Locale locale) {
        // Get coverage stats from database
        String coverageSql = """
        SELECT osm_id, h3_resolution, visited_cell_count, total_cell_count
        FROM h3_area_coverage_stats
        WHERE user_id = ? AND device_id = ? AND osm_id = ?
        """;

        List<CoverageStats> stats = jdbcTemplate.query(coverageSql,
                                                       (rs, rowNum) -> new CoverageStats(
                                                               rs.getLong("osm_id"),
                                                               rs.getInt("h3_resolution"),
                                                               rs.getInt("visited_cell_count"),
                                                               rs.getInt("total_cell_count")
                                                       ),
                                                       user.getId(), device != null ? device.id() : null, osmId
        );

        if (stats.isEmpty()) {
            return Optional.empty();
        }

        CoverageStats coverageStats = stats.getFirst();

        // Get localized name
        String name = getLocalizedName(osmId, locale);

        // Calculate coverage percentage
        double coveragePercentage = coverageStats.totalCells > 0 ?
                (double) coverageStats.visitedCells / coverageStats.totalCells * 100.0 : 0.0;

        // Optionally get individual visited cell IDs
        List<Long> visitedCellIds = getVisitedCellIds(user, osmId);

        return Optional.of(new CoverageInformation(
                osmId,
                name,
                coverageStats.totalCells,
                coverageStats.visitedCells,
                coveragePercentage,
                coverageStats.resolution,
                rocksDBService.getAdminLevel(osmId),
                visitedCellIds
        ));
    }


    /**
     * Returns coverage for a user across all devices while respecting timeline overrides.
     * This reads from the merged timeline view (v_source_stream) and maps the visited H3 cells
     * to OSM boundaries via RocksDB.
     *
     * @param user   the user
     * @param until  optional cut‑off timestamp; if null, all time up to now
     * @param locale for localised area names
     * @return coverage per OSM area (aggregated across resolutions)
     */
    @Override
    public List<CoverageInformation> getCoverage(User user, Instant until, Locale locale) {
        // 1. Get distinct H3 cells the user has ever visited, respecting timeline overrides.
        //    Because v_source_stream already picks the correct device for each timestamp,
        //    we can simply select distinct h3_cell values for the user up to the given time.
        String sql;
        List<Object> params;
        if (until != null) {
            sql = """
                    SELECT h3_cell
                    FROM v_source_stream
                    WHERE user_id = ? AND timestamp <= ?
                    GROUP BY h3_cell
                    """;
            params = List.of(user.getId(), Timestamp.from(until));
        } else {
            sql = """
                    SELECT h3_cell
                    FROM v_source_stream
                    WHERE user_id = ?
                    GROUP BY h3_cell
                    """;
            params = List.of(user.getId());
        }

        List<Long> visitedCells = jdbcTemplate.queryForList(sql, Long.class, params.toArray());

        return calculateCoverageInformation(locale, visitedCells);
    }

    /**
     * Returns a list of coverage entries for a single device. The coverage is computed
     * from the distinct H3 cells this device has ever visited, mapped to OSM boundaries
     * via RocksDB.
     *
     * @param user   the user who owns the device
     * @param device the device whose coverage to calculate
     * @param until  optional cut‑off timestamp; if not null, only cells with a
     *               {@code first_visited_at} on or before this moment are considered
     * @param locale for localised area names
     * @return a list of {@link CoverageInformation} objects – one per (OSM id, resolution) pair
     * where the device has at least one visited cell
     */
    @Override
    public List<CoverageInformation> getDeviceCoverage(User user, Device device,
                                                       Instant until, Locale locale) {
        // 1. Retrieve all distinct H3 cells for the specific device, optionally limited by time
        List<Long> cellIds;
        if (until != null) {
            cellIds = jdbcTemplate.query(
                    "SELECT DISTINCT h3_index FROM h3_cells_stats " +
                            "WHERE user_id = ? AND device_id = ? AND first_visited_at <= ?",
                    (rs, rowNum) -> rs.getLong("h3_index"),
                    user.getId(), device.id(), Timestamp.from(until));
        } else {
            cellIds = jdbcTemplate.query(
                    "SELECT DISTINCT h3_index FROM h3_cells_stats " +
                            "WHERE user_id = ? AND device_id = ?",
                    (rs, rowNum) -> rs.getLong("h3_index"),
                    user.getId(), device.id());
        }
        return calculateCoverageInformation(locale, cellIds);
    }

    private List<CoverageInformation> calculateCoverageInformation(Locale locale, List<Long> visitedCells) {
        Map<Long, Map<Integer, Set<Long>>> areaToResolutionVisitedCells = new HashMap<>();

        for (Long cellId : visitedCells) {
            List<RocksDBH3Service.CellWithBoundaries> boundaries = rocksDBService.getCellsWithBoundaries(cellId);
            for (RocksDBH3Service.CellWithBoundaries cwb : boundaries) {
                for (Long osmId : cwb.osmIds()) {
                    areaToResolutionVisitedCells
                            .computeIfAbsent(osmId, k -> new HashMap<>())
                            .computeIfAbsent(cwb.resolution(), k -> new HashSet<>())
                            .add(cwb.cellId());
                }
            }
        }

        Map<Long, Integer> adminLevelCache = new HashMap<>();
        List<CoverageInformation> result = new ArrayList<>();
        for (Map.Entry<Long, Map<Integer, Set<Long>>> entry : areaToResolutionVisitedCells.entrySet()) {
            long osmId = entry.getKey();
            Map<Integer, Set<Long>> resCounts = entry.getValue();

            int adminLevel = adminLevelCache.computeIfAbsent(osmId,
                    id -> rocksDBService.getAdminLevel(id));

            for (Map.Entry<Integer, Set<Long>> resEntry : resCounts.entrySet()) {
                int resolution = resEntry.getKey();
                long visited = resEntry.getValue().size();
                int totalCells = rocksDBService.getTotalCells(osmId, resolution);
                double pct = totalCells > 0 ? (double) visited / totalCells * 100.0 : 0.0;
                String name = getLocalizedName(osmId, locale);

                result.add(new CoverageInformation(
                        osmId, name, totalCells, (int) visited, pct, resolution, adminLevel,
                        List.of()
                ));
            }
        }
        return result;
    }

    private List<Long> getVisitedCellIds(User user, long osmId) {
        return List.of();
    }

    private String getLocalizedName(long osmId, Locale locale) {
        String nameSql = """
        SELECT COALESCE(
            all_names ->> ?,          -- Try locale-specific name (e.g., 'name:de')
            all_names ->> 'name',     -- Fallback to default 'name'
            'Area #' || ?             -- Final fallback with OSM ID
        ) as localized_name
        FROM osm_names
        WHERE osm_id = ? AND osm_type = 'R'
        """;

        String localeKey = "name:" + locale.getLanguage();

        try {
            String name = jdbcTemplate.queryForObject(nameSql, String.class,
                                                      localeKey, osmId, osmId);
            return name != null ? name : "Unknown Area #" + osmId;
        } catch (Exception e) {
            log.warn("Failed to get name for OSM ID {}: {}", osmId, e.getMessage());
            return "Unknown Area #" + osmId;
        }
    }

    public BoundingBox getOrComputeBounds(long osmId) {
        List<BoundingBox> cached = jdbcTemplate.query(
                "SELECT min_lat, min_lon, max_lat, max_lon FROM osm_bounds WHERE osm_id = ?",
                (rs, rowNum) -> new BoundingBox(
                        rs.getDouble("min_lat"), rs.getDouble("min_lon"),
                        rs.getDouble("max_lat"), rs.getDouble("max_lon")),
                osmId);
        if (!cached.isEmpty()) {
            return cached.getFirst();
        }

        byte[] wkb = rocksDBService.getBoundaryGeometry(osmId);
        if (wkb == null || wkb.length == 0) {
            return null;
        }

        try {
            WKBReader reader = new WKBReader(new GeometryFactory());
            Geometry geom = reader.read(wkb);
            org.locationtech.jts.geom.Envelope env = geom.getEnvelopeInternal();
            BoundingBox box = new BoundingBox(
                    env.getMinY(), env.getMinX(), env.getMaxY(), env.getMaxX());

            jdbcTemplate.update(
                    "INSERT INTO osm_bounds (osm_id, min_lat, min_lon, max_lat, max_lon) VALUES (?, ?, ?, ?, ?) ON CONFLICT (osm_id) DO NOTHING",
                    osmId, box.minLat, box.minLon, box.maxLat, box.maxLon);

            return box;
        } catch (Exception e) {
            log.warn("Failed to compute bounds for OSM ID {}: {}", osmId, e.getMessage());
            return null;
        }
    }

    public String getBoundaryGeoJson(long osmId) {
        byte[] wkb = rocksDBService.getBoundaryGeometry(osmId);
        if (wkb == null || wkb.length == 0) {
            return null;
        }

        try {
            WKBReader reader = new WKBReader(new GeometryFactory());
            Geometry geom = reader.read(wkb);
            return geometryToGeoJson(geom);
        } catch (Exception e) {
            log.warn("Failed to convert geometry for OSM ID {}: {}", osmId, e.getMessage());
            return null;
        }
    }

    private String geometryToGeoJson(Geometry geom) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"").append(geom.getGeometryType()).append("\",\"coordinates\":");
        appendCoordinates(sb, geom);
        sb.append("}");
        return sb.toString();
    }

    private void appendCoordinates(StringBuilder sb, Geometry geom) {
        String type = geom.getGeometryType();
        switch (type) {
            case "MultiPolygon" -> {
                sb.append("[");
                for (int i = 0; i < geom.getNumGeometries(); i++) {
                    if (i > 0) sb.append(",");
                    appendPolygonCoordinates(sb, geom.getGeometryN(i));
                }
                sb.append("]");
            }
            case "Polygon" -> appendPolygonCoordinates(sb, geom);
            default -> sb.append("[]");
        }
    }

    private void appendPolygonCoordinates(StringBuilder sb, Geometry polygon) {
        sb.append("[");
        for (int i = 0; i < polygon.getNumGeometries(); i++) {
            if (i > 0) sb.append(",");
            sb.append("[");
            Coordinate[] coords = polygon.getGeometryN(i).getCoordinates();
            for (int j = 0; j < coords.length; j++) {
                if (j > 0) sb.append(",");
                sb.append("[").append(coords[j].x).append(",").append(coords[j].y).append("]");
            }
            sb.append("]");
        }
        sb.append("]");
    }

    public List<CoverageInformation> getCoverageFiltered(User user, Instant until, Locale locale,
                                                          Double minLat, Double minLon,
                                                          Double maxLat, Double maxLon) {
        List<CoverageInformation> all = getCoverage(user, until, locale);

        if (minLat == null || maxLat == null) {
            return all;
        }

        return all.stream().filter(ci -> {
            BoundingBox box = getOrComputeBounds(ci.osmId());
            if (box == null) return true;
            return box.overlaps(minLat, minLon, maxLat, maxLon);
        }).toList();
    }

    public record BoundingBox(double minLat, double minLon, double maxLat, double maxLon) {
        public boolean overlaps(double qMinLat, double qMinLon, double qMaxLat, double qMaxLon) {
            return minLat <= qMaxLat && maxLat >= qMinLat
                    && minLon <= qMaxLon && maxLon >= qMinLon;
        }
    }

    private record CoverageStats(long osmId, int resolution, int visitedCells, int totalCells) {}
}
