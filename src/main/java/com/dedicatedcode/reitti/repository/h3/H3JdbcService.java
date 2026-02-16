package com.dedicatedcode.reitti.repository.h3;

import com.dedicatedcode.reitti.config.H3MappingConfig;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.H3Hexagon;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.PointReaderWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Service
@Transactional
public class H3JdbcService
{
    private final JdbcTemplate h3JdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final RowMapper<H3Hexagon> hexagonRowMapper;
    private final H3MappingConfig config;

    public H3JdbcService(@Qualifier("h3JdbcTemplate") JdbcTemplate h3JdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                         PointReaderWriter pointReaderWriter, H3MappingConfig config)
    {
        this.h3JdbcTemplate = h3JdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.hexagonRowMapper = (rs, _) ->
        {
            java.sql.Array sqlArray = rs.getArray("points");
            String[] array = (String[]) sqlArray.getArray();
            List<GeoPoint> points = Arrays.stream(array).map(pointReaderWriter::read).toList();

            return new H3Hexagon(rs.getString("h3Index"), rs.getTimestamp("timestamp").toInstant(),
                rs.getInt("resolution"), points, pointReaderWriter.read(rs.getString("first_seen")));
        };
        this.config = config;
    }

    public List<H3Hexagon> findH3PolygonsByUser(User user, double minLat, double maxLat, double minLon, double maxLon)
    {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("minLat", minLat);
        parameters.addValue("maxLat", maxLat);
        parameters.addValue("minLon", minLon);
        parameters.addValue("maxLon", maxLon);
        parameters.addValue("user_id", user.getId());

        String sql = """
            SELECT h3_index                    as h3Index,
                   rlp.timestamp               as timestamp,
                   h3_get_resolution(h3_index) as resolution,
                   ARRAY(
                           SELECT ST_AsText((dp).geom)
                           FROM ST_DumpPoints(h3_cell_to_boundary_geography(h3_mapping.h3_index)::geometry) AS dp
                           ORDER BY (dp).path
                   )                           AS "points",
                   ST_AsText(rlp.geom)         AS "first_seen"
            FROM h3_mapping
                     INNER JOIN raw_location_points AS rlp ON h3_mapping.first_seen = rlp.id
            WHERE user_id = :user_id AND ST_Within(rlp.geom, ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326));
            """;

        return namedParameterJdbcTemplate.query(sql, parameters, hexagonRowMapper);
    }

    public List<H3Hexagon> findH3PolygonsByUserDiscoveredDuringTime(User user, Instant startTime, Instant endTime,
                                                                    double minLat, double maxLat, double minLon,
                                                                    double maxLon)
    {
        var parameters = new MapSqlParameterSource();
        parameters.addValue("min_lat", minLat);
        parameters.addValue("max_lat", maxLat);
        parameters.addValue("min_lon", minLon);
        parameters.addValue("max_lon", maxLon);
        parameters.addValue("user_id", user.getId());
        parameters.addValue("start_time", Timestamp.from(startTime));
        parameters.addValue("end_time", Timestamp.from(endTime));

        String sql = """
            SELECT h3_index                    as h3Index,
                   rlp.timestamp               as timestamp,
                   h3_get_resolution(h3_index) as resolution,
                   ARRAY(
                           SELECT ST_AsText((dp).geom)
                           FROM ST_DumpPoints(h3_cell_to_boundary_geography(h3_mapping.h3_index)::geometry) AS dp
                           ORDER BY (dp).path
                   )                           AS "points",
                   ST_AsText(rlp.geom)         AS "first_seen"
            FROM h3_mapping
                     INNER JOIN raw_location_points AS rlp ON h3_mapping.first_seen = rlp.id
            WHERE user_id = :user_id AND ST_Within(rlp.geom, ST_MakeEnvelope(:min_lon, :min_lat, :max_lon, :max_lat, 4326))
              AND rlp.timestamp >= :start_time AND rlp.timestamp < :end_time;
            """;

        return namedParameterJdbcTemplate.query(sql, parameters, hexagonRowMapper);
    }

    public Long updateH3Mappings(int limit)
    {
        var namedParameters = new MapSqlParameterSource();
        namedParameters.addValue("resolution", config.getTargetResolution());
        namedParameters.addValue("limit", limit);
        namedParameters.addValue("neighbors", config.getH3RevealNeighbours());

        String createBatch = """
            CREATE TEMP TABLE batch_to_process AS
            SELECT id, h3_latlng_to_cell(geom, :resolution) as source_cell, timestamp
            FROM raw_location_points
            WHERE processed = true
              AND invalid = false
              AND h3_done = false
            LIMIT :limit;
        """;

        String insertIntoH3Mappings = """
            INSERT INTO h3_mapping (h3_index, first_seen)
            SELECT h3_cell, first_seen_id
            FROM (
                SELECT
                    neighbors.h3_cell,
                    (ARRAY_AGG(btp.id ORDER BY btp.timestamp))[1] as first_seen_id
                FROM batch_to_process btp
                CROSS JOIN LATERAL h3_grid_disk(btp.source_cell, :neighbors) AS neighbors(h3_cell)
                GROUP BY neighbors.h3_cell
            ) AS incoming_data
            ON CONFLICT (h3_index) DO UPDATE
            SET first_seen = EXCLUDED.first_seen
            WHERE (SELECT timestamp FROM raw_location_points WHERE id = h3_mapping.first_seen)
                > (SELECT timestamp FROM raw_location_points WHERE id = EXCLUDED.first_seen);
        """;

        String updateProcessed = """
            UPDATE raw_location_points
            SET h3_done = true
            WHERE id IN (SELECT id FROM batch_to_process);
        """;

        String getActualBatchSize = """
            SELECT COUNT(*) FROM batch_to_process;
        """;

        String dropBatch = """
            DROP TABLE batch_to_process;
        """;

        namedParameterJdbcTemplate.update(createBatch, namedParameters);
        namedParameterJdbcTemplate.update(insertIntoH3Mappings, namedParameters);
        h3JdbcTemplate.update(updateProcessed);
        long actualBatchSize = h3JdbcTemplate.queryForObject(getActualBatchSize, Long.class);
        h3JdbcTemplate.update(dropBatch);
        return actualBatchSize;
    }

    public Double getOverallAreaCovered(User user)
    {
        String sql = """
            SELECT sum(h3_cell_area(h3_mapping.h3_index, 'm^2'))
            FROM h3_mapping
                     INNER JOIN raw_location_points as rlp ON h3_mapping.first_seen = rlp.id
            where rlp.user_id = ?;
            """;
        return h3JdbcTemplate.queryForObject(sql, Double.class, user.getId());
    }

    public Double getCoveredAreaDuring(User user, Instant startTime, Instant endTime)
    {
        String sql = """
            SELECT sum(h3_cell_area(h3_mapping.h3_index, 'm^2'))
            FROM h3_mapping
                     INNER JOIN raw_location_points as rlp ON h3_mapping.first_seen = rlp.id
            where rlp.user_id = ? AND rlp.timestamp >= ? AND rlp.timestamp < ?;
            """;
        return h3JdbcTemplate.queryForObject(sql, Double.class, user.getId(), Timestamp.from(startTime),
            Timestamp.from(endTime));
    }

    public boolean checkH3MappingConsistency()
    {
        var sql = """
            SELECT COUNT(*) from (SELECT DISTINCT h3_get_resolution(h3_index) from h3_mapping)
            """;
        var numberOfDifferentResolutions = h3JdbcTemplate.queryForObject(sql, Integer.class);
        return numberOfDifferentResolutions <= 1;
    }

    public long getNotH3MappedRawLocationPointsCount() {
        var sql = """
            SELECT count(*) from raw_location_points where h3_done = false AND processed = true AND invalid = false;
            """;
        var missingMappings = h3JdbcTemplate.queryForObject(sql, Long.class);
        if (missingMappings == null) {
            // Actually, this should not be possible
            throw new RuntimeException("Invalid state exception");
        }
        return missingMappings;
    }

    /**
     * Returns the average size of a hexagon at a given resolution. This is a simple wrapper around the h3_avg_cell_area
     * function provided by the h3 postgres extension.
     *
     * @return Avg hexagon size in m^2
     */
    public double getHexAvgSizeM2(int resolution)
    {
        var sql = """
            SELECT h3_get_hexagon_area_avg(?, 'm^2');
            """;
        return h3JdbcTemplate.queryForObject(sql, Double.class, resolution);
    }

    public double getConfiguredHexAvgSizeM2()
    {
        return getHexAvgSizeM2(config.getTargetResolution());
    }

    /**
     * Helper function to get the avg reveal area given a resolution and neighbor count
     *
     * @return Avg reveal area in m^2
     */
    public double getRevealAvgAreaM2(int resolution, int neighbors)
    {
        return getHexAvgSizeM2(resolution) * numberOfNeighbourCells(neighbors);
    }

    public double getConfiguredRevealAvgAreaM2()
    {
        return getHexAvgSizeM2(config.getTargetResolution()) * numberOfNeighbourCells(config.getH3RevealNeighbours());
    }

    private int numberOfNeighbourCells(int neighbours)
    {
        var n = neighbours + 1;
        return 1 + 6 * (n * (n - 1) / 2); //See https://en.wikipedia.org/wiki/Centered_hexagonal_number
    }
}
