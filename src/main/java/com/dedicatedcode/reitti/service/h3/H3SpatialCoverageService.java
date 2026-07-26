package com.dedicatedcode.reitti.service.h3;

import com.dedicatedcode.reitti.model.CoverageInformation;
import com.dedicatedcode.reitti.model.devices.Device;
import com.dedicatedcode.reitti.model.security.User;
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
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@ConditionalOnProperty(name = "reitti.h3.enabled", havingValue = "true")
public class H3SpatialCoverageService implements SpatialCoverageService {
    private static final Logger log = LoggerFactory.getLogger(H3SpatialCoverageService.class);

    private final H3Core h3;
    private final RocksDBH3Service rocksDbService;
    private final JdbcTemplate jdbcTemplate;
    private final JobSchedulingService jobSchedulingService;
    private final JobDetail h3CellUpdateJob;

    public H3SpatialCoverageService(RocksDBH3Service rocksDbService,
                                    JdbcTemplate jdbcTemplate,
                                    JobSchedulingService jobSchedulingService,
                                    @Qualifier("h3CellUpdateTask") JobDetail h3CellUpdateJob) throws IOException {
        this.rocksDbService = rocksDbService;
        this.jdbcTemplate = jdbcTemplate;
        this.jobSchedulingService = jobSchedulingService;
        this.h3CellUpdateJob = h3CellUpdateJob;
        this.h3 = H3Core.newInstance();
    }

    @Override
    public Long getLevelCellForPoint(double latitude, double longitude, int resolution) {
        return h3.latLngToCell(latitude, longitude, resolution);
    }

    @Override
    public void postPromotion(List<Long> insertedIds) {
        this.jobSchedulingService.enqueueTask(h3CellUpdateJob,
                                              H3CellUpdateJob.TaskData.forPromotion(insertedIds),
                                              JobSchedulingService.Metadata.builder()
                                                      .jobType(JobType.H3_CELL_UPDATE)
                                                      .friendlyName("Updating H3 Spatial Statistics")
                                                      .build()
        );
    }

    @Override
    public void postDeletion(List<Long> deletedPointIds) {
        this.jobSchedulingService.enqueueTask(h3CellUpdateJob,
                                              H3CellUpdateJob.TaskData.forDeletion(deletedPointIds),
                                              JobSchedulingService.Metadata.builder()
                                                      .jobType(JobType.H3_CELL_UPDATE)
                                                      .friendlyName("Updating H3 Spatial Statistics")
                                                      .build()
        );
    }

    @Override
    public Optional<CoverageInformation> getCoverageInformation(User user, Device device, long osmId, Locale locale) {
        // Get coverage stats from database
        String coverageSql = """
        SELECT osm_id, visited_cell_count, total_cell_count
        FROM h3_area_coverage_stats
        WHERE user_id = ? AND osm_id = ?
        """;

        List<CoverageStats> stats = jdbcTemplate.query(coverageSql,
                                                       (rs, rowNum) -> new CoverageStats(
                                                               rs.getLong("osm_id"),
                                                               rs.getInt("visited_cell_count"),
                                                               rs.getInt("total_cell_count")
                                                       ),
                                                       user.getId(), osmId
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
        WHERE osm_id = ?
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
