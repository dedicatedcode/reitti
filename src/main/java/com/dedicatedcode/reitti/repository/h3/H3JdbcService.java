package com.dedicatedcode.reitti.repository.h3;

import com.dedicatedcode.reitti.config.H3MappingConfig;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.H3Hexagon;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.PointReaderWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(name = "reitti.h3.enabled", havingValue = "true")
@Transactional("h3TransactionManager")
public class H3JdbcService
{
    private final JdbcTemplate h3JdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterH3JdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final RowMapper<H3Hexagon> hexagonRowMapper;
    private final H3MappingConfig config;
    private final JdbcTemplate jdbcTemplate;

    public H3JdbcService(@Qualifier("h3JdbcTemplate") JdbcTemplate h3JdbcTemplate,
                         @Qualifier("namedParameterH3JdbcTemplate")
                         NamedParameterJdbcTemplate namedParameterH3JdbcTemplate,
                         NamedParameterJdbcTemplate namedParameterJdbcTemplate, PointReaderWriter pointReaderWriter,
                         H3MappingConfig config, JdbcTemplate jdbcTemplate)
    {
        this.h3JdbcTemplate = h3JdbcTemplate;
        this.namedParameterH3JdbcTemplate = namedParameterH3JdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.hexagonRowMapper = (rs, _) ->
        {
            java.sql.Array sqlArray = rs.getArray("points");
            String[] array = (String[]) sqlArray.getArray();
            List<GeoPoint> points = Arrays.stream(array).map(pointReaderWriter::read).toList();

            return new H3Hexagon(rs.getString("h3Index"), rs.getTimestamp("timestamp").toInstant(),
                rs.getInt("resolution"), points, rs.getLong("first_seen_index"));
        };
        this.config = config;
        this.jdbcTemplate = jdbcTemplate;
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
                   first_seen               as timestamp,
                   h3_get_resolution(h3_index) as resolution,
                   ARRAY(
                           SELECT ST_AsText((dp).geom)
                           FROM ST_DumpPoints(h3_cell_to_boundary_geography(h3_mapping.h3_index)::geometry) AS dp
                           ORDER BY (dp).path
                   )                           AS "points",
                   first_seen_index
            FROM h3_mapping
            WHERE user_id = :user_id AND ST_Within(h3_cell_to_geometry(h3_index), ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326));
            """;

        return namedParameterH3JdbcTemplate.query(sql, parameters, hexagonRowMapper);
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
                   first_seen               as timestamp,
                   h3_get_resolution(h3_index) as resolution,
                   ARRAY(
                           SELECT ST_AsText((dp).geom)
                           FROM ST_DumpPoints(h3_cell_to_boundary_geography(h3_mapping.h3_index)::geometry) AS dp
                           ORDER BY (dp).path
                   )                           AS "points",
                   first_seen_index
            FROM h3_mapping
            WHERE user_id = :user_id AND ST_Within(h3_cell_to_geometry(h3_index), ST_MakeEnvelope(:min_lon, :min_lat, :max_lon, :max_lat, 4326))
              AND first_seen >= :start_time AND first_seen < :end_time;
            """;

        return namedParameterH3JdbcTemplate.query(sql, parameters, hexagonRowMapper);
    }

    public int updateH3Mappings(int limit)
    {
        var lastProcessedId = getLatestMappedRawLocationPointIndex();

        var namedParameters = new MapSqlParameterSource();
        namedParameters.addValue("resolution", config.getTargetResolution());
        namedParameters.addValue("limit", limit);
        namedParameters.addValue("neighbors", config.getH3RevealNeighbours());
        namedParameters.addValue("lastProcessedId", lastProcessedId);

        String selectBatch = """
                SELECT id, ST_AsText(geom) as geom, timestamp, user_id
                FROM raw_location_points
                WHERE processed = true
                  AND invalid = false
                  AND id > :lastProcessedId
                LIMIT :limit;
            """;
        var batch = namedParameterJdbcTemplate.query(selectBatch, namedParameters,
            (rs, _) -> new H3MappingJob(rs.getLong("id"), rs.getString("geom"), rs.getTimestamp("timestamp"),
                rs.getLong("user_id")));
        if (batch.isEmpty())
        {
            return 0;
        }
        String createBatchTable = """
            CREATE TEMP TABLE batch_to_process (
                id BIGINT NOT NULL,
                h3index h3index NOT NULL,
                timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
                user_id BIGINT NOT NULL
            ) ON COMMIT DROP;
            """;
        namedParameterH3JdbcTemplate.update(createBatchTable, namedParameters);

        String fillBatchTable = """
                INSERT INTO batch_to_process (id, h3index, timestamp, user_id)
                VALUES (:id, h3_latlng_to_cell(ST_GeomFromText(:geom), :resolution), :timestamp, :user_id);
            """;
        var parameters = batch.stream().map(H3MappingJob::toNamedParameterSource).map(p -> p.addValues(namedParameters.getValues())).toArray(MapSqlParameterSource[]::new);
        namedParameterH3JdbcTemplate.batchUpdate(fillBatchTable, parameters);

        String insertIntoH3Mappings = """
                INSERT INTO h3_mapping (h3_index, first_seen_index, first_seen, user_id)
                SELECT h3_cell, first_seen_id, timestamp, user_id
                FROM (
                    SELECT
                        neighbors.h3_cell,
                        (ARRAY_AGG(btp.id ORDER BY btp.timestamp))[1] as first_seen_id,
                        (ARRAY_AGG(btp.timestamp ORDER BY btp.timestamp))[1] as timestamp,
                        (ARRAY_AGG(btp.user_id ORDER BY btp.timestamp))[1] as user_id
                    FROM batch_to_process btp
                    CROSS JOIN LATERAL h3_grid_disk(btp.h3index, :neighbors) AS neighbors(h3_cell)
                    GROUP BY neighbors.h3_cell
                ) AS incoming_data
                ON CONFLICT (h3_index, user_id) DO UPDATE
                SET first_seen = EXCLUDED.first_seen, first_seen_index = EXCLUDED.first_seen_index
                WHERE h3_mapping.first_seen > EXCLUDED.first_seen;
            """;
        namedParameterH3JdbcTemplate.update(insertIntoH3Mappings, namedParameters);

        batch.stream().map(H3MappingJob::id).max(Long::compare).ifPresent(this::setLatestMappedRawLocationPointIndex);
        return batch.size();
    }

    public long getLatestMappedRawLocationPointIndex()
    {
        String findLatestMappedIndexSql = """
            SELECT latest_mapped_index
            FROM latest_mapped_index
            WHERE mapping_index_type = 'raw_location_point_id'
            """;
        var latestMappedIndex = h3JdbcTemplate.queryForList(findLatestMappedIndexSql, Long.class);
        return latestMappedIndex.stream().findFirst().orElse(-1L);
    }

    public void setLatestMappedRawLocationPointIndex(long newLastLocationPointIndex)
    {
        String insertOrUpdate = """
            INSERT INTO latest_mapped_index(mapping_index_type, latest_mapped_index)
            VALUES ('raw_location_point_id', :newLastLocationPointIndex)
            ON CONFLICT (mapping_index_type) DO UPDATE
            SET latest_mapped_index = :newLastLocationPointIndex;
            """;
        namedParameterH3JdbcTemplate.update(insertOrUpdate,
            new MapSqlParameterSource("newLastLocationPointIndex", newLastLocationPointIndex));
    }

    public Double getOverallAreaCovered(User user)
    {
        String sql = """
            SELECT sum(h3_cell_area(h3_mapping.h3_index, 'm^2'))
            FROM h3_mapping
            where user_id = ?;
            """;
        return h3JdbcTemplate.queryForObject(sql, Double.class, user.getId());
    }

    public Double getCoveredAreaDuring(User user, Instant startTime, Instant endTime)
    {
        String sql = """
            SELECT sum(h3_cell_area(h3_index, 'm^2'))
            FROM h3_mapping
            where user_id = ? AND first_seen >= ? AND first_seen < ?;
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

    public long getNotH3MappedRawLocationPointsCount()
    {
        //TODO/NOTE: this currently includes points that have not been mapped but also cannot be mapped because there
        // are unprocessed points before them
        var latestMappedIndex = getLatestMappedRawLocationPointIndex();
        var sql = """
            SELECT count(*) from raw_location_points where processed = true AND invalid = false AND id > ?;
            """;

        var missingMappings = jdbcTemplate.queryForObject(sql, Long.class, latestMappedIndex);
        if (missingMappings == null)
        {
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

    private record H3MappingJob(Long id, String geom, Timestamp timestamp, Long userId)
    {
        public MapSqlParameterSource toNamedParameterSource()
        {
            return new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("geom", geom)
                .addValue("timestamp", timestamp)
                .addValue("user_id", userId);
        }
    }
}
