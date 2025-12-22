package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.Page;
import com.dedicatedcode.reitti.model.PageRequest;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.SignificantPlace;
import com.dedicatedcode.reitti.model.security.User;
import org.locationtech.jts.geom.Point;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service class for managing and accessing significant places using JDBC.
 * Provides methods for CRUD operations and queries related to significant places.
 * Includes support for handling geographical data and pagination.
 */
@Service
@Transactional
public class SignificantPlaceJdbcService {

    private final JdbcTemplate jdbcTemplate;
    private final PointReaderWriter  pointReaderWriter;
    private final RowMapper<SignificantPlace> significantPlaceRowMapper;

    public SignificantPlaceJdbcService(JdbcTemplate jdbcTemplate, PointReaderWriter pointReaderWriter) {
        this.jdbcTemplate = jdbcTemplate;
        this.pointReaderWriter = pointReaderWriter;
        this.significantPlaceRowMapper = (rs, _) -> new SignificantPlace(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("address"),
                rs.getString("city"),
                rs.getString("country_code"),
                rs.getDouble("latitude_centroid"),
                rs.getDouble("longitude_centroid"),
                pointReaderWriter.wktToPolygon(rs.getString("polygon")),
                SignificantPlace.PlaceType.valueOf(rs.getString("type")),
                rs.getString("timezone") != null ? ZoneId.of(rs.getString("timezone")) : null,
                rs.getBoolean("geocoded"),
                rs.getLong("version"));
    }

    public Page<SignificantPlace> findByUser(User user, PageRequest pageable) {
        String countSql = "SELECT COUNT(*) FROM significant_places WHERE user_id = ?";
        Integer total = jdbcTemplate.queryForObject(countSql, Integer.class, user.getId());

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
                   WHERE sp.user_id = ? ORDER BY sp.id
                 LIMIT ? OFFSET ?
                """;
        List<SignificantPlace> content = jdbcTemplate.query(sql, significantPlaceRowMapper,
                user.getId(), pageable.getPageSize(), pageable.getOffset());

        return new Page<>(content, pageable, total != null ? total : 0);
    }

    public Page<SignificantPlace> findByUserWithSearch(User user, PageRequest pageable, String search) {
        String searchCondition = (search != null && !search.trim().isEmpty()) ? "AND (name ILIKE ? OR address ILIKE ?)" : "";
        String countSql = "SELECT COUNT(*) FROM significant_places WHERE user_id = ? " + searchCondition;
        List<Object> countParams = new ArrayList<>();
        countParams.add(user.getId());
        if (search != null && !search.trim().isEmpty()) {
            countParams.add("%" + search.trim() + "%");
            countParams.add("%" + search.trim() + "%");
        }
        Integer total = jdbcTemplate.queryForObject(countSql, Integer.class, countParams.toArray());

        String sql = "SELECT sp.id,\n" +
                "                       sp.address,\n" +
                "                       sp.country_code,\n" +
                "                       sp.city,\n" +
                "                       sp.type,\n" +
                "                       sp.latitude_centroid,\n" +
                "                       sp.longitude_centroid,\n" +
                "                       sp.name,\n" +
                "                       sp.user_id,\n" +
                "                       ST_AsText(sp.geom) as geom,\n" +
                "                       ST_AsText(sp.polygon) as polygon,\n" +
                "                       sp.timezone,\n" +
                "                       sp.geocoded,\n" +
                "                       sp.version" +
                " FROM significant_places sp " +
                "WHERE sp.user_id = ? " + searchCondition + " ORDER BY sp.id " +
                "LIMIT ? OFFSET ? ";
        List<Object> params = new ArrayList<>(countParams);
        params.add(pageable.getPageSize());
        params.add(pageable.getOffset());
        List<SignificantPlace> content = jdbcTemplate.query(sql, significantPlaceRowMapper, params.toArray());

        return new Page<>(content, pageable, total != null ? total : 0);
    }

    /**
     * Searches for SignificantPlaces that are nearby to this point. This includes places with polygons
     * that are within the specified distance range of the given point, as well as places without polygons
     * whose center points are within the distance range.
     *
     * @param userId - the user to load the places for.
     * @param point - the point to search near.
     * @param distanceInDegrees - distance in degrees to search within.
     * @return list of nearby SignificantPlaces.
     */
    public List<SignificantPlace> findNearbyPlaces(Long userId, Point point, double distanceInDegrees) {
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
        WHERE sp.user_id = ?
        AND ST_DWithin(
            COALESCE(sp.polygon, sp.geom),
            ST_GeomFromText(?, '4326'),
            ?
        )
        """;

        return jdbcTemplate.query(sql, significantPlaceRowMapper,
                                  userId, point.toString(), distanceInDegrees);
    }

    /**
     * Searches for SignificantPlaces which contain this point. Either by having a polygon which contains that point or
     * by extending the center point by distanceInDegrees.
     *
     * @param userId - the user to load the places for.
     * @param point - the point to search for.
     * @param distanceInDegrees - meters in degress to extend the search radius for points without a polygon.
     * @return list of SignificantPlaces.
     */
    public List<SignificantPlace> findEnclosingPlaces(Long userId, Point point, double distanceInDegrees) {
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
        WHERE sp.user_id = ?
        AND ST_DWithin(
            COALESCE(sp.polygon, ST_Buffer(sp.geom, ?)),
            ST_GeomFromText(?, '4326'),
            0
        )
        """;

        return jdbcTemplate.query(sql, significantPlaceRowMapper,
                                  userId, distanceInDegrees, point.toString());
    }

    public SignificantPlace create(User user, SignificantPlace place) {

        String sql = "INSERT INTO significant_places (user_id, name, latitude_centroid, longitude_centroid, timezone, geom, polygon) " +
                "VALUES (?, ?, ?, ?, ?, ST_GeomFromText(?, '4326'), " +
                "CASE WHEN ?::text IS NOT NULL THEN ST_GeomFromText(?, '4326')  END) RETURNING id";
        ;

        String polygonWkt = this.pointReaderWriter.polygonToWkt(place.getPolygon());

        Long id = jdbcTemplate.queryForObject(sql, Long.class,
                user.getId(),
                place.getName(),
                place.getLatitudeCentroid(),
                place.getLongitudeCentroid(),
                place.getTimezone().getId(),
                this.pointReaderWriter.write(place.getLongitudeCentroid(), place.getLatitudeCentroid()),
                polygonWkt,
                polygonWkt
        );
        return findById(id).orElseThrow();
    }

    @CacheEvict(cacheNames = "significant-places", key = "#place.id")
    public SignificantPlace update(SignificantPlace place) {
        String sql = "UPDATE significant_places SET name = ?, address = ?, city = ?, country_code = ?, type = ?, " +
                "latitude_centroid = ?, longitude_centroid = ?, geom = ST_GeomFromText(?, '4326'), " +
                "polygon = CASE WHEN ?::text IS NOT NULL THEN ST_GeomFromText(?, '4326')  END, " +
                "timezone = ?, geocoded = ? WHERE id = ?";

        String polygonWkt = this.pointReaderWriter.polygonToWkt(place.getPolygon());

        jdbcTemplate.update(sql,
                            place.getName(),
                            place.getAddress(),
                            place.getCity(),
                            place.getCountryCode(),
                            place.getType().name(),
                            place.getLatitudeCentroid(),
                            place.getLongitudeCentroid(),
                            this.pointReaderWriter.write(place.getLongitudeCentroid(), place.getLatitudeCentroid()),
                            polygonWkt,
                            polygonWkt,
                            place.getTimezone() != null ? place.getTimezone().getId() : null,
                            place.isGeocoded(),
                            place.getId()
        );
        return findById(place.getId()).orElseThrow();    }

    @Cacheable("significant-places")
    public Optional<SignificantPlace> findById(Long id) {
        String sql = """
                SELECT sp.id,
                       sp.address,
                       sp.city,
                       sp.country_code,
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
                WHERE sp.id = ?
                """;
        List<SignificantPlace> results = jdbcTemplate.query(sql, significantPlaceRowMapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public boolean exists(User user, Long id) {
        return this.jdbcTemplate.queryForObject("SELECT count(*) FROM significant_places WHERE user_id = ? AND id = ?", Integer.class, user.getId(), id) > 0;
    }

    public List<SignificantPlace> findNonGeocodedByUser(User user) {
        String sql = """
                SELECT sp.id,
                       sp.address,
                       sp.city,
                       sp.country_code,
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
                WHERE sp.user_id = ? AND sp.geocoded = false
                ORDER BY sp.id
                """;
        return jdbcTemplate.query(sql, significantPlaceRowMapper, user.getId());
    }

    public List<SignificantPlace> findAllByUser(User user) {
        String sql = """
                SELECT sp.id,
                       sp.address,
                       sp.city,
                       sp.country_code,
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
                WHERE sp.user_id = ?
                ORDER BY sp.id
                """;
        return jdbcTemplate.query(sql, significantPlaceRowMapper, user.getId());
    }

    public List<SignificantPlace> findWithMissingTimezone() {
        String sql = """
                SELECT sp.id,
                       sp.address,
                       sp.city,
                       sp.country_code,
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
                 WHERE sp.timezone IS NULL
                 ORDER BY sp.id
                """;
        return jdbcTemplate.query(sql, significantPlaceRowMapper);

    }

    public void deleteForUser(User user) {
        this.jdbcTemplate.update("DELETE FROM geocoding_response WHERE significant_place_id IN (SELECT id FROM significant_places WHERE user_id = ?)", user.getId());
        this.jdbcTemplate.update("DELETE FROM significant_places WHERE user_id = ?", user.getId());
    }

    public List<SignificantPlace> findPlacesOverlappingWithPolygon(Long userId, Long excludePlaceId, List<GeoPoint> polygon) {
        if (polygon == null || polygon.size() < 3) {
            return List.of();
        }

        String sql = """
                SELECT sp.id,
                       sp.address,
                       sp.city,
                       sp.country_code,
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
                WHERE sp.user_id = ?
                AND sp.id != ?
                AND (
                    -- Check if the new polygon overlaps with existing place's polygon
                    (sp.polygon IS NOT NULL AND ST_Overlaps(sp.polygon, ST_GeomFromText(?, 4326)))
                    OR
                    -- Check if the new polygon contains the existing place's centroid
                    ST_Contains(ST_GeomFromText(?, 4326), sp.geom)
                    OR
                    -- Check if existing place's polygon contains any part of the new polygon
                    (sp.polygon IS NOT NULL AND ST_Overlaps(ST_GeomFromText(?, 4326), sp.polygon))
                )
                """;

        String polygonWkt = this.pointReaderWriter.polygonToWkt(polygon);
        return jdbcTemplate.query(sql, significantPlaceRowMapper, userId, excludePlaceId, polygonWkt, polygonWkt, polygonWkt);
    }
}
