package com.dedicatedcode.reitti.service.h3;

import com.dedicatedcode.reitti.dto.area.AreaBounds;
import com.dedicatedcode.reitti.dto.area.AreaDescription;
import com.dedicatedcode.reitti.dto.area.AreaType;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.H3Hexagon;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
@ConditionalOnProperty(name = "reitti.h3.area-mapping.enabled", havingValue = "true")
public class AreaJdbcService
{
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<SignificantPlace> significantPlaceRowMapper;
    private final RowMapper<AreaBounds> areaBoundsRowMapper;
    private final RowMapper<AreaDescription> areaDescriptionRowMapper;

    public AreaJdbcService(JdbcTemplate jdbcTemplate, RowMapper<SignificantPlace> significantPlaceRowMapper)
    {
        this.jdbcTemplate = jdbcTemplate;
        this.significantPlaceRowMapper = significantPlaceRowMapper;
        areaBoundsRowMapper =
            (rs, _) -> new AreaBounds(rs.getDouble("min_lat"), rs.getDouble("max_lat"), rs.getDouble("min_lon"),
                rs.getDouble("max_lon"));
        areaDescriptionRowMapper = (rs, _) -> new AreaDescription(AreaType.fromString(rs.getString("type")).get(), rs.getString("name"));
    }

    public Optional<Long> getAreaId(AreaDescription areaDescription)
    {
        var sql = """
            SELECT id
            FROM area
            WHERE type = CAST(? AS area_type)
              AND name = ?
              AND parent IS NULL
            """;
        var results =
            jdbcTemplate.queryForList(sql, Long.class, areaDescription.type().toString(), areaDescription.name());
        if (results.isEmpty())
        {
            return Optional.empty();
        }
        return Optional.of(results.getFirst());
    }

    public Optional<Long> getAreaId(AreaDescription areaDescription, long parentId)
    {
        var sql = """
                SELECT id
                FROM area
                WHERE type = CAST(? AS area_type)
                  AND name = ?
                  AND parent = ?
            """;
        var results =
            jdbcTemplate.queryForList(sql, Long.class, areaDescription.type().toString(), areaDescription.name(),
                parentId);
        if (results.isEmpty())
        {
            return Optional.empty();
        }
        return Optional.of(results.getFirst());
    }

    public Optional<Long> getAreaId(AreaDescription areaDescription, Long parentId)
    {
        if (parentId == null)
        {
            return getAreaId(areaDescription);
        } else
        {
            return getAreaId(areaDescription, parentId.longValue());
        }
    }

    public void connectAreaWithPlace(long areaId, long placeId)
    {
        var sql = """
            INSERT INTO area_significant_place_mapping(area_id, place_id) VALUES (?, ?) ON CONFLICT DO NOTHING;
            """;
        jdbcTemplate.update(sql, areaId, placeId);
    }

    public long storeUnmappedArea(AreaDescription areaDescription, @Nullable Long parentId)
    {
        var sql = """
            INSERT INTO area (type, name, boundary, parent) VALUES (CAST(? AS area_type), ?, NULL, ?) RETURNING id;
            """;
        var areaId =
            jdbcTemplate.queryForObject(sql, Long.class, areaDescription.type().toString(), areaDescription.name(),
                parentId);
        if (areaId == null)
        {
            throw new RuntimeException("Area not created");
        }
        return areaId;
    }

    public void markAreaAsMapped(long areaId, @Nullable String boundary)
    {
        if (boundary == null)
        {
            var sql = """
                UPDATE area SET boundaries_checked = true, last_checked = now() WHERE id = ? AND id != -1;
                """;
            jdbcTemplate.update(sql, areaId);
        } else
        {
            var sql = """
                UPDATE area SET boundaries_checked = true, boundary = ST_GeomFromGeoJSON(?), last_checked = now() WHERE id = ? AND id != -1;
                """;
            jdbcTemplate.update(sql, boundary, areaId);
        }
    }

    /**
     * Checks all yet unchecked h3 mappings against specified area
     *
     * @param areaId
     */
    public void updateAreaMapping(long areaId)
    {
        String setAreaForUnmapped = """
            UPDATE h3_mapping
            SET
                city_id    = CASE WHEN b.type = 'city'    THEN b.id ELSE city_id END,
                country_id = CASE WHEN b.type = 'country' THEN b.id ELSE country_id END,
                county_id  = CASE WHEN b.type = 'county'  THEN b.id ELSE county_id END,
                state_id   = CASE WHEN b.type = 'state'   THEN b.id ELSE state_id END
            FROM (
                SELECT id, type, boundary
                FROM area
                WHERE id = ? AND boundary IS NOT NULL AND id != -1
            ) AS b
            WHERE
                (
                    (b.type = 'city'    AND h3_mapping.city_id = -1) OR
                    (b.type = 'country' AND h3_mapping.country_id = -1) OR
                    (b.type = 'county'  AND h3_mapping.county_id = -1) OR
                    (b.type = 'state'   AND h3_mapping.state_id = -1)
                )
                AND ST_Intersects(
                    b.boundary,
                    ST_SetSRID(h3_cell_to_latlng(h3_mapping.h3_index)::geometry, 4326)
                );
            """;
        jdbcTemplate.update(setAreaForUnmapped, areaId);
    }

    public List<SignificantPlace> findSignificantPlacesWithoutAreaMapping()
    {
        String sql = """
            SELECT sp.id,
                   sp.address,
                   sp.country_code,
                   sp.city,
                   sp.type,
                   sp.latitude_centroid,
                   sp.longitude_centroid,
                   sp.name,
                   sp.user_id,
                   ST_AsText(sp.geom) as geom,
                   ST_AsText(sp.polygon) as polygon,
                   sp.timezone,
                   sp.geocoded,
                   sp.version
             FROM significant_places sp
             WHERE id NOT IN (SELECT place_id FROM area_significant_place_mapping)
            """;
        return jdbcTemplate.query(sql, significantPlaceRowMapper);
    }

    public int updatePointsAreaBatch(int limit)
    {
        String sql = """
            UPDATE h3_mapping
            SET
                city_id    = COALESCE(updates.city_id, -1),
                country_id = COALESCE(updates.country_id, -1),
                county_id  = COALESCE(updates.county_id, -1),
                state_id   = COALESCE(updates.state_id, -1)
            FROM (
                SELECT h3_index
                FROM h3_mapping
                WHERE city_id IS NULL
                   OR country_id IS NULL
                   OR state_id IS NULL
                   OR county_id IS NULL
                LIMIT ?
                FOR UPDATE SKIP LOCKED
            ) AS target
            LEFT JOIN LATERAL (
                SELECT
                    MAX(id) FILTER (WHERE type = 'city')    AS city_id,
                    MAX(id) FILTER (WHERE type = 'country') AS country_id,
                    MAX(id) FILTER (WHERE type = 'county')  AS county_id,
                    MAX(id) FILTER (WHERE type = 'state')   AS state_id
                FROM area
                WHERE area.boundary IS NOT NULL AND ST_Intersects(
                    area.boundary,
                    ST_SetSRID(h3_cell_to_latlng(target.h3_index)::geometry, 4326)
                )
            ) AS updates ON TRUE
            WHERE h3_mapping.h3_index = target.h3_index;
            """;
        return jdbcTemplate.update(sql, limit);
    }

    public int getUnmappedH3IndexCount()
    {
        var sql = """
            SELECT COUNT(*) FROM h3_mapping
            WHERE city_id IS NULL
               OR country_id IS NULL
               OR state_id IS NULL
               OR county_id IS NULL;
            """;
        var count = jdbcTemplate.queryForObject(sql, Integer.class);
        if (count == null)
        {
            //Should not be possible
            throw new RuntimeException("Invalid state");
        }
        return count;
    }

    /**
     * Retrieves all unmapped area ids.
     * <p>
     * Returns empty list if no such area exists.
     */
    public List<Long> getUnmappedAreaIds()
    {
        var sql = """
            WITH RECURSIVE area_drilldown AS (
                SELECT id, parent, boundaries_checked, 1 AS depth
                FROM area
                WHERE parent IS NULL AND id != -1
            
                UNION ALL
            
                SELECT a.id, a.parent, a.boundaries_checked, ad.depth + 1
                FROM area a
                INNER JOIN area_drilldown ad ON a.parent = ad.id
            )
            SELECT id
            FROM area_drilldown
            WHERE boundaries_checked = false AND id != -1
            ORDER BY depth;
            """;
        return jdbcTemplate.queryForList(sql, Long.class);
    }

    /**
     * Returns the geofence of the specified area or the first parent with a valid boundary.
     * <p>
     * Returns null if no boundary exists and no parent has a valid boundary.
     *
     * @return Geofence of the area or first parent with a valid boundary if area has none
     */
    public Optional<AreaBounds> getAreaBestGeoFence(long areaId)
    {
        String sql = """
            WITH RECURSIVE area_hierarchy AS (
                    SELECT id, parent, boundary, 1 AS depth
                    FROM area
                    WHERE id = ?
                UNION ALL
                    SELECT b.id, b.parent, b.boundary, bh.depth + 1
                    FROM area b
                    INNER JOIN area_hierarchy bh ON b.id = bh.parent
                    WHERE bh.boundary IS NULL
            )
            SELECT
                ST_YMin(boundary) AS min_lat,
                ST_YMax(boundary) AS max_lat,
                ST_XMin(boundary) AS min_lon,
                ST_XMax(boundary) AS max_lon
            FROM area_hierarchy
            WHERE boundary IS NOT NULL AND id != -1
            ORDER BY depth
            LIMIT 1;
            """;

        var maybeArea = jdbcTemplate.query(sql, areaBoundsRowMapper, areaId);
        if (maybeArea.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(maybeArea.getFirst());
    }

    public Optional<AreaDescription> getArea(long areaId)
    {
        String sql = """
            SELECT name, type
            FROM area
            WHERE id = ? AND id != -1;
            """;
        var maybeArea = jdbcTemplate.query(sql, areaDescriptionRowMapper, areaId);
        if (maybeArea.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(maybeArea.getFirst());
    }

    public List<AreaDescription> getAreaParents(long areaId)
    {
        String sql = """
            WITH RECURSIVE parents AS (
                SELECT id, parent, name, type
                FROM area
                WHERE id = ?
            
                UNION ALL
            
                SELECT a.id, a.parent, a.name, a.type
                FROM area a
                INNER JOIN parents p ON a.id = p.parent
            )
            SELECT name, type
            FROM parents
            WHERE id != ? AND id != -1;
            """;
        return jdbcTemplate.queryForList(sql, AreaDescription.class, areaId, areaId);
    }

    public double getAreaSize(long areaId)
    {
        //TODO: check area id is >= 0
        //TODO: check area exists
        String sql = """
                SELECT ST_AREA(boundary, true) FROM area WHERE id = ? AND id != -1;
            """;
        return jdbcTemplate.queryForObject(sql, Double.class, areaId);
    }
}