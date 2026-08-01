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
import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
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

    public H3SpatialCoverageService(JdbcTemplate jdbcTemplate,
                                    JobSchedulingService jobSchedulingService,
                                    @Qualifier("h3CellUpdateTask") JobDetail h3CellUpdateJob, PointReaderWriter pointReaderWriter) throws IOException {
        this.jdbcTemplate = jdbcTemplate;
        this.jobSchedulingService = jobSchedulingService;
        this.h3CellUpdateJob = h3CellUpdateJob;
        this.pointReaderWriter = pointReaderWriter;
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
            List<H3CellUpdateJob.MovedPoint> result = this.jdbcTemplate.query("SELECT ST_AsText(geom) AS geom_wkt FROM raw_source_points WHERE id = ? ", (rs, rowNum) -> {
                GeoPoint geomWkt = pointReaderWriter.read(rs.getString("geom_wkt"));
                return new H3CellUpdateJob.MovedPoint(movedPointDto.getSourceId(), geomWkt.latitude(), geomWkt.longitude(), movedPointDto.getLat(), movedPointDto.getLng());
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
    public Optional<CoverageInformation> getCoverageInformation(User user, Device device, long osmId, Locale locale) {
        // Get coverage stats from database
        String coverageSql = """
        SELECT osm_id, visited_cell_count, total_cell_count
        FROM h3_area_coverage_stats
        WHERE user_id = ? AND device_id = ? AND osm_id = ?
        """;

        List<CoverageStats> stats = jdbcTemplate.query(coverageSql,
                                                       (rs, rowNum) -> new CoverageStats(
                                                               rs.getLong("osm_id"),
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
                visitedCellIds
        ));
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

    private record CoverageStats(long osmId, int visitedCells, int totalCells) {}
}
